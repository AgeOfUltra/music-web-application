package com.music.musicwebapplication.service;

import com.music.musicwebapplication.controller.ChatController;
import com.music.musicwebapplication.entity.Participant;
import com.music.musicwebapplication.entity.Room;
import com.music.musicwebapplication.entity.UserSession;
import com.music.musicwebapplication.exception.RoomManageException;
import com.music.musicwebapplication.exception.RoomNotFoundException;
import com.music.musicwebapplication.repo.ParticipantRepo;
import com.music.musicwebapplication.repo.RoomRepo;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class RoomService {
    private final RoomRepo repo;
    private final ParticipantRepo participantRepo;
    private final ConfessService serviceConfess;
    private final PlaybackStateService playbackStateService;
   private final UserSessionService sessionService;

    private final ConcurrentHashMap<String, String> exitingUsers = new ConcurrentHashMap<>();

    public RoomService(RoomRepo repo, ParticipantRepo participantRepo, ConfessService serviceConfess, PlaybackStateService playbackStateService, UserSessionService sessionService) {
        this.repo = repo;
        this.participantRepo = participantRepo;
        this.serviceConfess = serviceConfess;
        this.playbackStateService = playbackStateService;
        this.sessionService = sessionService;
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



    @Transactional
    public boolean exitFromRoom(String roomName, String userName, boolean clearCompleteSession) {
        // ✅ PREVENT RACE CONDITION: Check if user is already exiting
        String exitKey = roomName + ":" + userName;

        // Try to mark user as exiting (returns null if already exiting)
        if (exitingUsers.putIfAbsent(exitKey, "EXITING") != null) {
            log.warn("⚠️ User {} already exiting from room {} - skipping duplicate call", userName, roomName);
            return false;
        }

        try {
            // Add a small lock to prevent concurrent database operations
            synchronized (exitKey.intern()) {

                Optional<Room> roomOpt = repo.findRoomByRoomName(roomName); // check with roomHash
                if (roomOpt.isEmpty()) {
                    log.warn("⚠️ Room {} not found - already deleted", roomName);

                    // Clean up session if needed
                    UserSession session = sessionService.getUserSession(userName);
                    if (session != null && roomName.equals(session.getRoomName())) {
                        if (clearCompleteSession) {
                            sessionService.deleteUserSession(userName);
                            log.info("Session DELETED for {} (room already deleted)", userName);
                        } else {
                            sessionService.updateRoomName(userName, null);
                            log.info("RoomName CLEARED for {} (room already deleted)", userName);
                        }
                    }
                    return false;
                }

                Room room = roomOpt.get();
                List<Participant> participants = room.getParticipant();

                // Check if user is actually in the room
                Optional<Participant> leavingUserOpt = participants.stream()
                        .filter(p -> p.getUserName().equals(userName))
                        .findFirst();

                if (leavingUserOpt.isEmpty()) {
                    log.warn("⚠️ User {} not found in room {} - already removed", userName, roomName);

                    // Clean up session if user has stale room reference
                    UserSession session = sessionService.getUserSession(userName);
                    if (session != null && roomName.equals(session.getRoomName())) {
                        if (clearCompleteSession) {
                            sessionService.deleteUserSession(userName);
                            log.info("Session DELETED for {} (user already removed)", userName);
                        } else {
                            sessionService.updateRoomName(userName, null);
                            log.info("RoomName CLEARED for {} (user already removed)", userName);
                        }
                    }
                    return false;
                }

                Participant leavingUser = leavingUserOpt.get();
                boolean isOrganizer = leavingUser.isOrganizer();
                int currentSize = participants.size();

                // ---------- LAST ORGANIZER: DELETE ROOM + REDIS ----------
                if (currentSize == 1 && isOrganizer) {
                    repo.delete(room);
                    playbackStateService.clearFavorites(roomName);
                    playbackStateService.clearPlaybackState(roomName);

                    // ✅ Session handling based on exit type
                    if (clearCompleteSession) {
                        sessionService.deleteUserSession(userName);
                        log.info("Room {} deleted. Session DELETED for {}", roomName, userName);
                    } else {
                        sessionService.updateRoomName(userName, null);
                        log.info("Room {} deleted. RoomName CLEARED for {}", roomName, userName);
                    }
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

                    // 🆕 Clear playback state when organizer changes
                    playbackStateService.clearPlaybackState(roomName);
                    log.info("🧹 Cleared playback state due to organizer transfer in room {}", roomName);
                }

                // ---------- REMOVE PARTICIPANT ----------
                participants.remove(leavingUser);
                leavingUser.setRoom(null);
                leavingUser.setOrganizer(false);
                participantRepo.delete(leavingUser);
                repo.save(room);

                // ✅ Session handling based on exit type
                if (clearCompleteSession) {
                    sessionService.deleteUserSession(userName);
                    log.info("User {} removed from room {}. Session DELETED.", userName, roomName);
                } else {
                    sessionService.updateRoomName(userName, null);
                    log.info("User {} removed from room {}. RoomName CLEARED.", userName, roomName);
                }

                return true;
            }
        } finally {
            // ✅ Always remove the exit lock after operation completes
            exitingUsers.remove(exitKey);
            log.debug("🔓 Released exit lock for {}", exitKey);
        }
    }

    // ✅ Keep backward compatibility - default to clearing room only
    @Transactional
    public boolean exitFromRoom(String roomName, String userName) {
        return exitFromRoom(roomName, userName, false);
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

    public List<String> getAllActiveRoomNames() {
        return repo.findAllRoomName();
    }
}

