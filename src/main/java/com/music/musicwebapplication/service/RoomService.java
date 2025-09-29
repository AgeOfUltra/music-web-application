package com.music.musicwebapplication.service;

import com.music.musicwebapplication.entity.Participant;
import com.music.musicwebapplication.entity.Room;
import com.music.musicwebapplication.exception.RoomManageException;
import com.music.musicwebapplication.exception.RoomNotFoundException;
import com.music.musicwebapplication.repo.ParticipantRepo;
import com.music.musicwebapplication.repo.RoomRepo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class RoomService {
    private final RoomRepo repo;
    private final ParticipantRepo participantRepo;


    public RoomService(RoomRepo repo, ParticipantRepo participantRepo) {
        this.repo = repo;
        this.participantRepo = participantRepo;
    }

    public Room createRoom(String roomName, int size , String userName, boolean isOrganizer){

        Optional<Room> existingRoom = repo.findRoomByRoomName(roomName);
        if(existingRoom.isPresent()){
            throw new RoomManageException("Room already exist with given name");
        }
        Room room = new Room();
        room.setRoomName(roomName);
        room.setMaxCount(size);
        Participant participant = new Participant();
        participant.setUserName(userName);
        participant.setOrganizer(isOrganizer);
        participant.setRoom(room);
        participantRepo.save(participant);
        room.getParticipant().add(participant);
        return repo.save(room);
    }

    @Transactional
    public Room joinRoom(String roomName, String userName, boolean isOrganizer){
        Room room = repo.findRoomByRoomName(roomName)
                .orElseThrow(() -> new RoomNotFoundException("Room Not found with Room Name: " + roomName));

        boolean isUserExist = room.getParticipant().stream()
                .anyMatch(u -> u.getUserName().equals(userName));
        if(isUserExist){
            throw new RoomManageException("User already exist in the room");
        }

        if(room.getParticipant().size() >= room.getMaxCount()){
            throw new RoomManageException("Room is full, can't join the room");
        }

        Participant participant = new Participant();
        participant.setUserName(userName);
        participant.setOrganizer(isOrganizer);
        participant.setRoom(room);
        participantRepo.save(participant); // This sets both sides automatically

        return repo.findById(room.getId()).orElse(room);
    }

    public boolean exitFromRoom(String roomName, String userName) {
        Optional<Room> existingRoom = repo.findRoomByRoomName(roomName);
        if (existingRoom.isEmpty()) {
            throw new RoomNotFoundException("Room not found with Room Name: " + roomName);
        }

        Room room = existingRoom.get();
        List<Participant> existingParticipants = room.getParticipant();
        Optional<Participant> selectParticipant = existingParticipants.stream()
                .filter(p -> p.getUserName().equals(userName))
                .findFirst();

        if (selectParticipant.isEmpty()) {
            throw new IllegalArgumentException("User " + userName + " is not in room: " + roomName);
        }


        if (room.getParticipant().isEmpty()) {
            repo.delete(room);
            return true;
        }
        Participant participant = selectParticipant.get();
        return  room.getParticipant().remove(participant);

    }

    public Room getRoomDetails(String roomId){
        Optional<Room> room = repo.findRoomByRoomName(roomId);
        if(room.isEmpty()){
            throw new RoomNotFoundException("Room Not found with Room Name: "+roomId);
        }else{
            return room.get();
        }
    }
}

