package com.music.musicwebapplication.service;

import com.music.musicwebapplication.entity.Participant;
import com.music.musicwebapplication.entity.Room;
import com.music.musicwebapplication.exception.RoomNotFoundException;
import com.music.musicwebapplication.repo.RoomRepo;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

@Service
public class RoomService {
    private final RoomRepo repo;


    public RoomService(RoomRepo repo) {
        this.repo = repo;
    }

    public Room createRoom(String roomId, int size , String userName, boolean isOrganizer){
        Room room = new Room();
        room.setRoomName(roomId);
        room.setMaxCount(size);
        Participant participant = new Participant();
        participant.setUserName(userName);
        participant.setOrganizer(isOrganizer);
        Set<Participant> participants = new HashSet<>();
        participants.add(participant);
        room.setParticipant(participants);
        return repo.save(room);
    }

    public Room joinRoom(String roomId, String userName, boolean isOrganizer){
        Optional<Room> existingRoom = repo.findRoomByRoomName(roomId);

        if(existingRoom.isEmpty()){
            throw new RoomNotFoundException("Room Not found with Room Name: "+roomId);
        }


        Participant participant = new Participant();
        participant.setUserName(userName);
        participant.setOrganizer(isOrganizer);
        Set<Participant> participants = new HashSet<>();
        participants.add(participant);
        existingRoom.get().setParticipant(participants);

        return repo.save(existingRoom.get());
    }

    public Room exitFromRoom(String roomId, String userName){
        Optional<Room> existingRoom = repo.findRoomByRoomName(roomId);
        if(existingRoom.isEmpty()){
            throw new RoomNotFoundException("Room Not found with Room Name: "+roomId);
        }
        Room room = existingRoom.get();
        Set<Participant> existingPart = room.getParticipant();
        Optional<Participant> selectParticipant = existingPart.stream().filter(p->p.getUserName().equals(userName)).findFirst();

        Participant participant = selectParticipant.get();
        room.getParticipant().remove(participant);
        return  repo.save(room);
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

