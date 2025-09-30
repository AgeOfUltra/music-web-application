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

    public Room createRoom(Room room){

        Optional<Room> existingRoom = repo.findRoomByRoomName(room.getRoomName());
        if(existingRoom.isPresent()){
            throw new RoomManageException("Room already exist with given name");
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

        if(participant.isOrganizer()){
            Participant nextParticipant =getNextAvailablePerson(participant.getId());
            nextParticipant.setOrganizer(true);
            participantRepo.save(nextParticipant);
        }
        participantRepo.delete(participant);
        return  true;

    }

    public Room getRoomDetails(String roomName){
        return repo.findRoomByRoomName(roomName).orElseThrow(() -> new RoomNotFoundException("Room not found with Room Name: " + roomName));
    }

    private Participant getNextAvailablePerson(long id){
        Optional<Participant> nextParticipant = participantRepo.findById(id);
        if(nextParticipant.isPresent()){
            return nextParticipant.get();
        }else{
            return getNextAvailablePerson(id++);
        }
    }
}

