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


    public RoomService(RoomRepo repo, ParticipantRepo participantRepo) {
        this.repo = repo;
        this.participantRepo = participantRepo;
    }
    @Transactional
    public Room createRoom(Room room){
        Optional<Room> existingRoom = repo.findRoomByRoomName(room.getRoomName());
        if(isUserPresentInAnyRoom(room.getParticipant().get(0).getUserName())){
            throw new RoomManageException("User already exist in one of the room");
        }

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
        Optional<Room> existingRoom = repo.findRoomByRoomName(roomName);
        if (existingRoom.isEmpty()) {
            throw new RoomNotFoundException("Room not found with Room Name: " + roomName);
        }

        List<Participant> existingParticipants = existingRoom.get().getParticipant();

        Optional<Participant> selectParticipant =  existingRoom.get().getParticipant().stream()
                .filter(p -> p.getUserName().equals(userName))
                .findFirst();

        if (selectParticipant.isEmpty()) {
            throw new IllegalArgumentException("User " + userName + " is not in room: " + roomName);
        }

        if(existingRoom.get().getParticipant().size() == 1 && selectParticipant.get().isOrganizer()){
            repo.delete(existingRoom.get());
            return true;
        }

        if(existingRoom.get().getParticipant().size() >1 && selectParticipant.get().isOrganizer()){
            Optional<Participant> newOrganizer = existingParticipants.stream().filter(p -> !p.getUserName().equals(userName)).findFirst();
            if(newOrganizer.isPresent()){
                newOrganizer.get().setOrganizer(true);
                participantRepo.save(newOrganizer.get());
            }

        }

        existingRoom.get().getParticipant().remove(selectParticipant.get());
        selectParticipant.get().setOrganizer(false);
        selectParticipant.get().setRoom(null);
        repo.save(existingRoom.get());
        return  true;

    }
    @Transactional
    public Room getRoomDetails(String roomName){
        return repo.findRoomByRoomName(roomName).orElseThrow(() -> new RoomNotFoundException("Room not found with Room Name: " + roomName));
    }

//    private Participant getNextAvailablePerson(long id, long roomId){
//        Optional<Participant> nextParticipant = participantRepo.findById(id);
//        if(nextParticipant.isPresent()){
//            return nextParticipant.get();
//        }else if(participantRepo.countParticipantByRoomId(roomId) - 1 > 0){
//            return getNextAvailablePerson(id++,roomId);
//        }else{
//            return null;
//        }
//    }

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

