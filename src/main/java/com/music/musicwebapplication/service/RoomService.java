package com.music.musicwebapplication.service;

import com.music.musicwebapplication.entity.Participant;
import com.music.musicwebapplication.entity.Room;
import com.music.musicwebapplication.exception.RoomManageException;
import com.music.musicwebapplication.exception.RoomNotFoundException;
import com.music.musicwebapplication.repo.ParticipantRepo;
import com.music.musicwebapplication.repo.RoomRepo;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class RoomService {
    private final RoomRepo repo;
    private final ParticipantRepo participantRepo;
    private final ConfessService serviceConfess;


    public RoomService(RoomRepo repo, ParticipantRepo participantRepo, ConfessService serviceConfess) {
        this.repo = repo;
        this.participantRepo = participantRepo;
        this.serviceConfess = serviceConfess;
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


        if (currentSize == 1 && isOrganizer) {
            repo.delete(room);
            return true;
        }


        if (isOrganizer && currentSize > 1) {
            participants.stream()
                    .filter(p -> !p.getUserName().equals(userName))
                    .findFirst()
                    .ifPresent(newOrg -> {
                        newOrg.setOrganizer(true);
                        participantRepo.save(newOrg);
                    });
        }


        participants.remove(leavingUser);
        leavingUser.setRoom(null);
        leavingUser.setOrganizer(false);

        repo.save(room);

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

    @Transactional
    public Optional<String> exitFromRoomLogoutHandler(String username) {
        Optional<Room> userRoom = repo.findAll()
                .stream()
                .filter(room -> room.getParticipant()
                        .stream()
                        .anyMatch(p -> p.getUserName().equals(username)))
                .findFirst();

        if (userRoom.isEmpty()) {
            return Optional.empty();
        }

        String roomName = userRoom.get().getRoomName();
        try {
            exitFromRoom(roomName, username);
            return Optional.of(roomName);
        } catch (Exception e) {
            log.error("Error while removing user {} from room {}", username, roomName, e);
            return Optional.empty();
        }
    }



    @PreDestroy
    private void clearRooms(){
        log.info("All rooms deletion started...");
        repo.deleteAll();
        log.info("All rooms deletion completed...");
    }
}

