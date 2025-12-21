package com.music.musicwebapplication.service;

import com.music.musicwebapplication.dto.ConfessContainerRequest;
import com.music.musicwebapplication.entity.Confess;
import com.music.musicwebapplication.repo.ConfessRepo;
import com.music.musicwebapplication.enums.Role;
import com.music.musicwebapplication.enums.Status;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;


import java.util.*;
import java.util.random.RandomGenerator;

@Slf4j
@Service
public class ConfessService {

    private final ConfessRepo repo;
    private static final RandomGenerator RNG = RandomGenerator.of("L128X256MixRandom");
    private static final String CHARSET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$&";

    @Value("${app.music.room.length}")
    private int ROOM_LENGTH;

    @Value("${app.music.pass.code.length}")
    private int PASSCODE_LENGTH;


    public ConfessService(ConfessRepo repo) {
        this.repo = repo;
    }

    public String buildSaveConfessData(ConfessContainerRequest cr){
        //generate : room-name
        String roomHash = generateRoomName(cr.getMessage().substring(4,11),cr.getReceiverAlias(),cr.getConfessType(),cr.getSingerName(),cr.getSongName(),cr.getRoomName());
//        passcode generate
        String passCode = generatePassCode();
//        created time stamp need to update and duration will be handled later upon open.

//        here we don't have fall back-mechanism , if the duplicate room hash is found
        Confess entity = new Confess();
        entity.setInitiatedBy(cr.getInitiatedBy());
        entity.setSenderOriginalName(cr.getSenderOriginalName());
        entity.setSenderEmail(cr.getSenderEmail());
        entity.setReceiverAlias(cr.getReceiverAlias());
        entity.setConfessType(cr.getConfessType());
        entity.setEmail(cr.getEmail());
        entity.setPasscode(passCode);
        entity.setSongName(cr.getSongName());
        entity.setSingerName(cr.getSingerName());
        entity.setMessage(cr.getMessage());
        entity.setRoomName(cr.getRoomName());
        entity.setStatus(Status.IN_PROGRESS);
        entity.setRoomHash(roomHash);
        entity.setRole(Role.GUEST);

        Confess result =  repo.save(entity);
        return  result.getId() >-1 ? "SUCCESS" : "FAILED";
    }

    private String generateRoomName(String message,String alias,String type,String sender,String song,String roomName){
        String newStr = (message+alias+type+sender+song+roomName).replace(" ","");
        return generateHashHelper(newStr);
    }
    public String generateRoomName(String roomName, int size, String organizer){
        String newStr = (organizer+roomName+Integer.toString(size)).replace(" ","");
        return generateHashHelper(newStr);
    }
    private String generateHashHelper(String newStr) {
        StringBuilder sb = new StringBuilder(ROOM_LENGTH);
        for (int i = 0; i < ROOM_LENGTH; i++) {
            int index = RNG.nextInt(newStr.length());
            sb.append(newStr.charAt(index));
        }

        String hash= sb.toString();
        log.info("room has generated {} ",hash);
        return hash;
    }

    public String generatePassCode(){

        StringBuilder sb = new StringBuilder(PASSCODE_LENGTH);
        for (int i = 0; i < PASSCODE_LENGTH; i++) {
            int index = RNG.nextInt(CHARSET.length());
            sb.append(CHARSET.charAt(index));
        }
        String passCode = sb.toString();
        log.info("passcode generated {}",passCode);
        return passCode;
    }

//    update STATUS 1 -> STATUS 2

    public Map<String, String> updateStatus(Status s1, Status s2,String roomHash){
        log.info("initiating the status update service process ,Request : {},{},{}",s1,s2,roomHash);
        Optional<Confess> availableRequest = repo.findByRoomHash(roomHash);

        Map<String,String> response = new HashMap<>();
        if(availableRequest.isEmpty()){
            response.put("error","No Data is available");
            log.error("Failed to update the process");
            return  response;
        }
//        TODO :  need to find the failure case.
        if(availableRequest.get().getStatus().equals(s1)){
            availableRequest.get().setStatus(s2);
            Confess updatedConfess = repo.save(availableRequest.get());
            log.info("updated the status successfully new updated data: {}",updatedConfess);
            response.put("saved","data saved successfully");
        }
        return response;
    }

    public Optional<List<Confess>> getAllRequestForUser(String initiatedBy) {
        return repo.findByInitiatedBy(initiatedBy);
    }

    public Optional<List<Confess>> getAllInProgressRequest(Status status) {
        return repo.findByStatus(status);
    }
    public Optional<Confess> getDetailsByRoomHash(String roomHash){
        return repo.findByRoomHash(roomHash);
    }
}
