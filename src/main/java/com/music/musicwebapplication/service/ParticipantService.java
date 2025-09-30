package com.music.musicwebapplication.service;

import com.music.musicwebapplication.entity.Participant;
import com.music.musicwebapplication.entity.Room;
import com.music.musicwebapplication.exception.RoomNotFoundException;
import com.music.musicwebapplication.repo.ParticipantRepo;
import com.music.musicwebapplication.repo.RoomRepo;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class ParticipantService {
    private  final ParticipantRepo repo;
    private final RoomRepo rRepo;
    public ParticipantService(ParticipantRepo repo, RoomRepo rRepo) {
        this.repo = repo;
        this.rRepo = rRepo;
    }

    public Participant addParticipant(String roomName,Participant participant){
        Optional<Room> existingRoom = rRepo.findRoomByRoomName(roomName);
        if(existingRoom.isPresent()){
            Room room = existingRoom.get();
            participant.setRoom(room);
            return repo.save(participant);
        }else{
            throw new RoomNotFoundException("Room not found with name: " + roomName);
        }
    }
}


