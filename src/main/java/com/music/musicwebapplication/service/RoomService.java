package com.music.musicwebapplication.service;

import com.music.musicwebapplication.entity.Participant;
import com.music.musicwebapplication.entity.Room;
import com.music.musicwebapplication.entity.UserSession;
import com.music.musicwebapplication.exception.RoomManageException;
import com.music.musicwebapplication.exception.RoomNotFoundException;
import com.music.musicwebapplication.repo.ParticipantRepo;
import com.music.musicwebapplication.repo.RoomRepo;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.List;
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
        log.debug("RoomService initialized");
    }

    @Transactional
    public Room createRoom(Room room){
        log.info("Attempting to create room: {}", room.getRoomName());
        Optional<Room> existingRoom = repo.findRoomByRoomName(room.getRoomName());
        String participantName = room.getParticipant().get(0).getUserName();
        log.debug("Room creator: {}", participantName);

        if(isUserPresentInAnyRoom(participantName)){
            log.warn("Room creation failed - user {} already exists in another room", participantName);
            throw new RoomManageException("User already exist in one of the room");
        }

//        set room name and then generate passcode from existing method
        String roomHash = serviceConfess.generateRoomName(room.getRoomName(), room.getMaxCount(), participantName);
        String passCode = serviceConfess.generatePassCode();
        room.setRoomHash(roomHash);
        room.setPassCode(passCode);
        log.debug("Generated room hash: {} and passcode for room: {}", roomHash, room.getRoomName());

        if(existingRoom.isPresent()){
            log.warn("Room creation failed - room already exists with name: {}", room.getRoomName());
            throw new RoomManageException("Room already available with given name");
        }

        List<Participant> participant = room.getParticipant();
        participant.forEach(p->{
            if(p!=null){
                p.setRoom(room);
            }
        });

        Room savedRoom = saveRoomInDbWithRetry(room);
        log.info("Room created successfully: {}, hash: {}", room.getRoomName(), roomHash);
        return savedRoom;
    }

    @Retryable(
            retryFor = {SocketException.class, SocketTimeoutException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public Room saveRoomInDbWithRetry(Room r){
        try{
            return repo.save(r);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    @Retryable(
            retryFor = {SocketException.class, SocketTimeoutException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public Participant saveParticipantInDbWithRetry(Participant p){
        try{
            return participantRepo.save(p);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    @Transactional
    public Participant joinRoom(String roomName, Participant participant){
        log.info("User {} attempting to join room: {}", participant.getUserName(), roomName);

        Room room = repo.findRoomByRoomName(roomName)
                .orElseThrow(() -> {
                    log.error("Room not found: {}", roomName);
                    return new RoomNotFoundException("Room Not found with Room Name: " + roomName);
                });

        boolean isUserExist = room.getParticipant().stream()
                .anyMatch(u -> u.getUserName().equals(participant.getUserName()));
        if(isUserExist){
            log.warn("Join failed - user {} already exists in room: {}", participant.getUserName(), roomName);
            throw new RoomManageException("User already exist in the room");
        }

        if(room.getParticipant().size() >= room.getMaxCount()){
            log.warn("Join failed - room {} is full (current: {}, max: {})", roomName, room.getParticipant().size(), room.getMaxCount());
            throw new RoomManageException("Room is full, can't join the room");
        }

        participant.setRoom(room);
        Participant savedParticipant = saveParticipantInDbWithRetry(participant);
        log.info("User {} successfully joined room: {}", participant.getUserName(), roomName);
        return savedParticipant;
    }



    @Transactional
    public boolean exitFromRoom(String roomName, String userName, boolean clearCompleteSession) {
        log.info("User {} exiting from room: {}, clear complete session: {}", userName, roomName, clearCompleteSession);

        // PREVENT RACE CONDITION: Check if user is already exiting
        String exitKey = roomName + ":" + userName;

        // Try to mark user as exiting (returns null if already exiting)
        if (exitingUsers.putIfAbsent(exitKey, "EXITING") != null) {
            log.warn("User {} already exiting from room {} - skipping duplicate call", userName, roomName);
            return false;
        }

        try {
            // Add a small lock to prevent concurrent database operations
            synchronized (exitKey.intern()) {

                Optional<Room> roomOpt = repo.findRoomByRoomName(roomName); // check with roomHash
                if (roomOpt.isEmpty()) {
                    log.warn("Room {} not found - already deleted", roomName);

                    // Clean up session if needed
                    UserSession session = sessionService.getUserSession(userName);
                    if (session != null && roomName.equals(session.getRoomName())) {
                        if (clearCompleteSession) {
                            sessionService.deleteUserSession(userName);
                            log.info("Session deleted for user {} (room already deleted)", userName);
                        } else {
                            sessionService.updateRoomName(userName, null);
                            log.info("Room name cleared for user {} (room already deleted)", userName);
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
                    log.warn("User {} not found in room {} - already removed", userName, roomName);

                    // Clean up session if user has stale room reference
                    UserSession session = sessionService.getUserSession(userName);
                    if (session != null && roomName.equals(session.getRoomName())) {
                        if (clearCompleteSession) {
                            sessionService.deleteUserSession(userName);
                            log.info("Session deleted for user {} (user already removed)", userName);
                        } else {
                            sessionService.updateRoomName(userName, null);
                            log.info("Room name cleared for user {} (user already removed)", userName);
                        }
                    }
                    return false;
                }

                Participant leavingUser = leavingUserOpt.get();
                boolean isOrganizer = leavingUser.isOrganizer();
                int currentSize = participants.size();
                log.debug("User {} leaving room {}, is organizer: {}, current size: {}", userName, roomName, isOrganizer, currentSize);

                // ---------- LAST ORGANIZER: DELETE ROOM + REDIS ----------
                if (currentSize == 1 && isOrganizer) {
                    log.info("Last organizer {} leaving room {} - deleting room", userName, roomName);
                    repo.delete(room);
                    playbackStateService.clearFavorites(roomName);
                    playbackStateService.clearPlaybackState(roomName);
                    log.debug("Cleared playback state and favorites for room: {}", roomName);

                    // Session handling based on exit type
                    if (clearCompleteSession) {
                        sessionService.deleteUserSession(userName);
                        log.info("Room {} deleted, session deleted for user: {}", roomName, userName);
                    } else {
                        sessionService.updateRoomName(userName, null);
                        log.info("Room {} deleted, room name cleared for user: {}", roomName, userName);
                    }
                    return true;
                }

                // ---------- TRANSFER ORGANIZER ROLE ----------
                if (isOrganizer && currentSize > 1) {
//                    log.info("Organizer {} leaving room {} - transferring organizer role", userName, roomName);
                    participants.stream()
                            .filter(p -> !p.getUserName().equals(userName))
                            .findFirst()
                            .ifPresent(newOrg -> {
                                newOrg.setOrganizer(true);
                                participantRepo.save(newOrg);
                                log.info("Organizer role transferred from {} to {} in room {}", userName, newOrg.getUserName(), roomName);
                            });

                    // Clear playback state when organizer changes
                    playbackStateService.clearPlaybackState(roomName);
                    log.info("Cleared playback state due to organizer transfer in room: {}", roomName);
                }

                // ---------- REMOVE PARTICIPANT ----------
                log.debug("Removing participant {} from room: {}", userName, roomName);
                participants.remove(leavingUser);
                leavingUser.setRoom(null);
                leavingUser.setOrganizer(false);
                participantRepo.delete(leavingUser);
                saveRoomInDbWithRetry(room);

                // Session handling based on exit type
                if (clearCompleteSession) {
                    sessionService.deleteUserSession(userName);
                    log.info("User {} removed from room {}, session deleted", userName, roomName);
                } else {
                    sessionService.updateRoomName(userName, null);
                    log.info("User {} removed from room {}, room name cleared", userName, roomName);
                }

                return true;
            }
        } finally {
            // Always remove the exit lock after operation completes
            exitingUsers.remove(exitKey);
            log.debug("Released exit lock for: {}", exitKey);
        }
    }

    // Keep backward compatibility - default to clearing room only
    @Transactional
    public boolean exitFromRoom(String roomName, String userName) {
        log.debug("Exit from room called without clearCompleteSession flag for user: {} in room: {}", userName, roomName);
        return exitFromRoom(roomName, userName, false);
    }


    @Transactional
    public Room getRoomDetails(String roomName){
        log.debug("Fetching room details for room: {}", roomName);
        return repo.findRoomByRoomName(roomName).orElseThrow(() -> {
            log.error("Room not found: {}", roomName);
            return new RoomNotFoundException("Room not found with Room Name: " + roomName);
        });
    }

    @Transactional
    public Optional<Room> getRoomDetailsByHash(String hash){
        log.debug("Fetching room details by hash: {}", hash);
        Optional<Room> room = repo.findRoomWithParticipantsByRoomHash(hash);
        if(room.isPresent()){
            log.debug("Room found for hash: {}", hash);
        } else {
            log.debug("No room found for hash: {}", hash);
        }
        return room;
    }


    public boolean isUserPresentInAnyRoom(String username){
        log.debug("Checking if user {} is present in any room", username);
        boolean isPresent = repo.findAll().stream()
                .anyMatch(room -> room.getParticipant()
                        .stream().anyMatch(u->u.getUserName().equals(username)));
        log.debug("User {} present in any room: {}", username, isPresent);
        return isPresent;
    }

    public boolean isUserOrganizer(String roomName, String username) {
        log.debug("Checking if user {} is organizer of room: {}", username, roomName);
        Optional<Room> room = repo.findRoomByRoomName(roomName);
        boolean isOrganizer = room.map(value -> value.getParticipant().stream()
                .anyMatch(p -> p.getUserName().equals(username) && p.isOrganizer())).orElse(false);
        log.debug("User {} is organizer of room {}: {}", username, roomName, isOrganizer);
        return isOrganizer;
    }

    @PreDestroy
    private void clearRooms(){
        log.info("RoomService shutting down - starting room deletion");
        try {
            repo.deleteAll();
            log.info("All rooms deleted successfully");
        } catch (Exception e) {
            log.error("Error during room deletion: {}", e.getMessage(), e);
        }
    }

    public List<String> getAllActiveRoomNames() {
        log.debug("Fetching all active room names");
        List<String> roomNames = repo.findAllRoomName();
        log.debug("Found {} active rooms", roomNames.size());
        return roomNames;
    }
}