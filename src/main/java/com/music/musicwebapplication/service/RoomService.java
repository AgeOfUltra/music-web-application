package com.music.musicwebapplication.service;

import com.music.musicwebapplication.controller.ChatController;
import com.music.musicwebapplication.entity.Participant;
import com.music.musicwebapplication.entity.Room;
import com.music.musicwebapplication.exception.RoomManageException;
import com.music.musicwebapplication.exception.RoomNotFoundException;
import com.music.musicwebapplication.repo.ParticipantRepo;
import com.music.musicwebapplication.repo.RoomRepo;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
public class RoomService {
    private final RoomRepo repo;
    private final ParticipantRepo participantRepo;
    private final ConfessService serviceConfess;
    private final PlaybackStateService playbackStateService;
   private final UserSessionService sessionService;
    private final SimpMessagingTemplate messagingTemplate;

    public RoomService(RoomRepo repo, ParticipantRepo participantRepo, ConfessService serviceConfess, PlaybackStateService playbackStateService, UserSessionService sessionService, SimpMessagingTemplate messagingTemplate) {
        this.repo = repo;
        this.participantRepo = participantRepo;
        this.serviceConfess = serviceConfess;
        this.playbackStateService = playbackStateService;
        this.sessionService = sessionService;
        this.messagingTemplate = messagingTemplate;
    }
    @Transactional
    public Room createRoom(Room room){
        Optional<Room> existingRoom = repo.findRoomByRoomName(room.getRoomName());
        String participantName = room.getParticipant().get(0).getUserName();
        if(isUserPresentInAnyRoom(participantName)){
            throw new RoomManageException("User already exist in one of the room");
        }
//        set room name and then generate passcode from existing method
        room.setRoomHash(serviceConfess.generateRoomName(room.getRoomName(), room.getMaxCount(),participantName ));
        room.setPassCode(serviceConfess.generatePassCode());
        if(existingRoom.isPresent()){
            throw new RoomManageException("Room already available with given name");
        }

        List<Participant> participant = room.getParticipant();
        participant.forEach(p->{
            if(p!=null){
                p.setRoom(room);
            }
        });
        return repo.save(room);
    }

    @Transactional
    public Participant joinRoom(String roomName, Participant participant){
        Room room = repo.findRoomByRoomName(roomName)
                .orElseThrow(() -> new RoomNotFoundException("Room Not found with Room Name: " + roomName));

        boolean isUserExist = room.getParticipant().stream()
                .anyMatch(u -> u.getUserName().equals(participant.getUserName()));
        if(isUserExist){
            throw new RoomManageException("User already exist in the room");
        }

        if(room.getParticipant().size() >= room.getMaxCount()){
            throw new RoomManageException("Room is full, can't join the room");
        }
        participant.setRoom(room);
        return participantRepo.save(participant);
    }

    /**
     * Handles complete room exit with WebSocket notifications
     * Returns the room name that was exited from
     */
    @Transactional
    public Optional<String> exitFromRoomWithNotification(String username) {
        // Get room name from session
        Optional<String> roomNameOpt = sessionService.getRoomName(username);

        if (roomNameOpt.isEmpty()) {
            log.warn("User {} attempted to exit but is not in any room", username);
            return Optional.empty();
        }

        String roomName = roomNameOpt.get();

        // Check if room exists and user is actually in it
        Optional<Room> roomOpt = repo.findRoomByRoomName(roomName);
        if (roomOpt.isEmpty()) {
            log.warn("Room {} not found for user {}", roomName, username);
            // Still clear user session even if room doesn't exist
            playbackStateService.clearFavorites(roomName);
            playbackStateService.clearPlaybackState(roomName);
            return Optional.empty();
        }

        Room room = roomOpt.get();
        boolean userExists = room.getParticipant().stream()
                .anyMatch(p -> p.getUserName().equals(username));

        if (!userExists) {
            log.warn("User {} not found in room {}", username, roomName);
            return Optional.empty();
        }

        // Check if user is the last organizer (room will be deleted)
        boolean isLastOrganizer = room.getParticipant().size() == 1 &&
                room.getParticipant().get(0).isOrganizer() &&
                room.getParticipant().get(0).getUserName().equals(username);

        // ✅ Clear user-specific Redis data before exit
//        playbackStateService.(roomName, username);

        // 1. Remove user from room (handles organizer transfer, room deletion, Redis cleanup)
        boolean roomDeleted = exitFromRoom(roomName, username);

        // 2. Only broadcast if room still exists (not deleted)
        if (!isLastOrganizer && !roomDeleted) {
            try {
                // Broadcast LEAVE message via WebSocket
                Map<String, Object> leaveMessage = new HashMap<>();
                leaveMessage.put("sender", username);
                leaveMessage.put("type", "LEAVE");
                leaveMessage.put("content", username + " left the room");

                messagingTemplate.convertAndSend("/topic/chat/" + roomName, leaveMessage);

                // Broadcast updated participants list
                Optional<Room> updatedRoom = repo.findRoomByRoomName(roomName);
                if (updatedRoom.isPresent()) {
                    List<Participant> participants = updatedRoom.get().getParticipant();
                    messagingTemplate.convertAndSend("/topic/chat/" + roomName + "/participants", participants);
                    log.info("✅ Broadcasted participant update for room {}", roomName);
                }
            } catch (Exception e) {
                log.error("Error broadcasting room exit for user {} in room {}: {}",
                        username, roomName, e.getMessage());
            }
        } else {
            log.info("Room {} was deleted after last organizer {} left", roomName, username);
        }

        return roomNameOpt;
    }

    @Transactional
    public boolean exitFromRoom(String roomName, String userName) {

        Room room = repo.findRoomByRoomName(roomName)
                .orElseThrow(() -> new RoomNotFoundException("Room not found: " + roomName));

        List<Participant> participants = room.getParticipant();

        Participant leavingUser = participants.stream()
                .filter(p -> p.getUserName().equals(userName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("User " + userName + " is not in room: " + roomName));

        boolean isOrganizer = leavingUser.isOrganizer();
        int currentSize = participants.size();

        // ---------- LAST ORGANIZER: DELETE ROOM + REDIS ----------
        if (currentSize == 1 && isOrganizer) {
            repo.delete(room);

            // ✅ Clear ALL Redis data for the room
            playbackStateService.clearFavorites(roomName);
            playbackStateService.clearPlaybackState(roomName);

            log.info("Room {} deleted. All Redis data cleared.", roomName);

            return true;
        }

        // ---------- TRANSFER ORGANIZER ROLE ----------
        if (isOrganizer && currentSize > 1) {
            participants.stream()
                    .filter(p -> !p.getUserName().equals(userName))
                    .findFirst()
                    .ifPresent(newOrg -> {
                        newOrg.setOrganizer(true);
                        participantRepo.save(newOrg);
                        log.info("Organizer role transferred from {} to {} in room {}",
                                userName, newOrg.getUserName(), roomName);
                    });

            // ✅ OPTIONAL: Clear playback state when organizer changes
            // Uncomment if you want to reset playback on organizer transfer
            // playbackStateService.clearPlaybackState(roomName);
        }

        // ---------- REMOVE PARTICIPANT ----------
        participants.remove(leavingUser);
        leavingUser.setRoom(null);
        leavingUser.setOrganizer(false);

        participantRepo.delete(leavingUser); // ✅ Delete from DB
        repo.save(room);

        log.info("User {} removed from room {}. Remaining participants: {}",
                userName, roomName, participants.size());

        return true;
    }


    @Transactional
    public Room getRoomDetails(String roomName){
        return repo.findRoomByRoomName(roomName).orElseThrow(() -> new RoomNotFoundException("Room not found with Room Name: " + roomName));
    }

    @Transactional
    public Optional<Room> getRoomDetailsByHash(String hash){
        return repo.findRoomWithParticipantsByRoomHash(hash);
    }


    public boolean isUserPresentInAnyRoom(String username){
        return repo.findAll().stream()
                .anyMatch(room -> room.getParticipant()
                        .stream().anyMatch(u->u.getUserName().equals(username)));
    }

    public boolean isUserOrganizer(String roomName, String username) {
        Optional<Room> room = repo.findRoomByRoomName(roomName);
        return room.map(value -> value.getParticipant().stream()
                .anyMatch(p -> p.getUserName().equals(username) && p.isOrganizer())).orElse(false);

    }





    @PreDestroy
    private void clearRooms(){
        log.info("All rooms deletion started...");
        repo.deleteAll();
        log.info("All rooms deletion completed...");
    }
}

