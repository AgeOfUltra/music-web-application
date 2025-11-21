package com.music.musicwebapplication.service;

import com.music.musicwebapplication.dto.ConfessContainerRequest;
import com.music.musicwebapplication.entity.Confess;
import com.music.musicwebapplication.repo.ConfessRepo;
import com.music.musicwebapplication.support.Role;
import com.music.musicwebapplication.support.Status;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.random.RandomGenerator;

@Slf4j
@Service
public class ConfessService {

    private final ConfessRepo repo;
    private static final RandomGenerator RNG = RandomGenerator.of("L128X256MixRandom");
    private static final String CHARSET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private final ModelMapper modelMapper;


    public ConfessService(ConfessRepo repo, ModelMapper modelMapper) {
        this.repo = repo;
        this.modelMapper = modelMapper;
    }

    public String buildSaveConfessData(ConfessContainerRequest cr){
        //generate : room-name
        String roomHash = generateRoomName(cr.getMessage().substring(4,11),cr.getReceiverAlias(),cr.getConfessType(),cr.getSingerName(),cr.getSongName(),cr.getConfessRoomName(),8);
//        passcode generate
        String passCode = generatePassCode(5);
//        created time stamp need to update and duration will be handled later upon open.

//        here we don't have fall back-mechanism , if the duplicate room hash is found
        Status status = Status.IN_PROGRESS;
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

        entity.setCreatedAt(Timestamp.from(Instant.now()));
        entity.setRoomName(cr.getConfessRoomName());
        entity.setStatus(status);
        entity.setRoomHash(roomHash);
        entity.setRole(Role.GUEST);

        Confess result =  repo.save(entity);
        return  result.getId() >-1 ? "SUCCESS" : "FAILED";
    }

    private String generateRoomName(String message,String alias,String type,String sender,String song,String roomName,int length){
        String newStr = (message+alias+type+sender+song+roomName).replace(" ","");
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            int index = RNG.nextInt(newStr.length());
            sb.append(newStr.charAt(index));
        }

        String hash= sb.toString();
        log.info("room has generated {} ",hash);
        return hash;
    }
    private String generatePassCode(int length){

        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            int index = RNG.nextInt(CHARSET.length());
            sb.append(CHARSET.charAt(index));
        }
        String passCode = sb.toString();
        log.info("passcode generated {}",passCode);
        return passCode;
    }

    public Optional<List<ConfessContainerRequest>> getConfessData(Status status){

        log.info("confss request type {}",status.toString());
        List<Confess> confessStatus = repo.findAll().stream().filter(c -> c.getStatus().equals(status)).toList();

        log.info("confess status result:{}",confessStatus);

        List<ConfessContainerRequest> result = new ArrayList<>();
        result.addAll(confessStatus.stream().map(c -> modelMapper.map(c, ConfessContainerRequest.class)).toList());

        log.info("confess data result:{}",result);
        return Optional.of(result);
    }
}
