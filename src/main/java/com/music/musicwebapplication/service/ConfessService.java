package com.music.musicwebapplication.service;

import com.music.musicwebapplication.dto.ConfessDto;
import com.music.musicwebapplication.entity.Confess;
import com.music.musicwebapplication.repo.ConfessRepo;
import com.music.musicwebapplication.enums.Role;
import com.music.musicwebapplication.enums.Status;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.security.SecureRandom;
import java.util.*;
import java.util.random.RandomGenerator;

@Slf4j
@Service
public class ConfessService {

    private final ConfessRepo repo;
    private static final SecureRandom RNG = new SecureRandom();
    private static final String CHARSET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$&";


    @Value("${app.music.room.length}")
    private int ROOM_LENGTH;

    @Value("${app.music.pass.code.length}")
    private int PASSCODE_LENGTH;



    public ConfessService(ConfessRepo repo) {
        this.repo = repo;
        log.debug("ConfessService initialized");
    }

    public String buildSaveConfessData(ConfessDto cr) {
        log.info("Building and saving confess data for user: {}", cr.getInitiatedBy());
        //generate : room-name
        String roomHash = generateRoomName(cr.getMessage().substring(4, 11), cr.getReceiverAlias(), cr.getConfessType(), cr.getSingerName(), cr.getSongName(), cr.getRoomName());
//        passcode generate
        String passCode = generatePassCode();
//        created time stamp need to update and duration will be handled later upon open.

//        here we don't have fall back-mechanism , if the duplicate room hash is found
        log.debug("Generated room hash: {} and passcode for confess", roomHash);

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

        try{
            Confess result = saveConfessInDbWithRetry(entity);
            log.info("Confess data saved successfully with id: {}", result.getId());
            return result.getId() > -1 ? "SUCCESS" : "FAILED";
        }catch(Exception e){
            log.error("Error saving confess data: {}", e.getMessage(), e);
            return "FAILED";
        }
    }
    @Retryable(
            retryFor = {SocketException.class, SocketTimeoutException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public Confess saveConfessInDbWithRetry(Confess u){
        try{
            return repo.save(u);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    private String generateRoomName(String message, String alias, String type, String sender, String song, String roomName) {
        log.debug("Generating room name from parameters");
        String newStr = (message + alias + type + sender + song + roomName).replace(" ", "");
        return generateHashHelper(newStr);
    }

    public String generateRoomName(String roomName, int size, String organizer) {
        log.debug("Generating room name for organizer: {}, size: {}", organizer, size);
        String newStr = (organizer + roomName + Integer.toString(size)).replace(" ", "");
        return generateHashHelper(newStr);
    }

    private String generateHashHelper(String newStr) {
        StringBuilder sb = new StringBuilder(ROOM_LENGTH);
        for (int i = 0; i < ROOM_LENGTH; i++) {
            int index = RNG.nextInt(newStr.length());
            sb.append(newStr.charAt(index));
        }

        String hash = sb.toString();
        log.debug("Room hash generated: {}", hash);
        return hash;
    }

    public String generatePassCode() {

        StringBuilder sb = new StringBuilder(PASSCODE_LENGTH);
        for (int i = 0; i < PASSCODE_LENGTH; i++) {
            int index = RNG.nextInt(CHARSET.length());
            sb.append(CHARSET.charAt(index));
        }
        String passCode = sb.toString();
        log.debug("Passcode generated successfully");
        return passCode;
    }

    //    update STATUS 1 -> STATUS 2
    @Transactional
    public Map<String, String> updateStatus(ConfessDto request) {
        log.info("Updating confess status to {} for room hash: {}, initiated by: {}",
                request.getStatus(), request.getRoomHash(), request.getInitiatedBy());
        Optional<Confess> availableRequest = repo.findByRoomHash(request.getRoomHash());

        Map<String, String> serviceResponse = new HashMap<>();
        if (availableRequest.isEmpty()) {
            serviceResponse.put("error", "No Data is available");
            log.warn("No confess data found for room hash: {}", request.getRoomHash());
            return serviceResponse;
        }
//        TODO :  need to find the failure case.
        if (request.getStatus().equals(Status.APPROVED) || request.getStatus().equals(Status.REJECTED) ) {
            availableRequest.get().setStatus(request.getStatus());
            availableRequest.get().setNote(request.getNote());
            try{
                Confess updatedConfess = saveConfessInDbWithRetry(availableRequest.get());
                log.info("Confess status updated successfully to {} for id: {}",
                        updatedConfess.getStatus(), updatedConfess.getId());
                serviceResponse.put("saved", "Data saved successfully");
            }catch(Exception e){
                log.error("Error updating confess status: {}", e.getMessage(), e);
                serviceResponse.put("error", "Failed to save data");
            }
        }else{
            serviceResponse.put("error", "Not a valid Request");
            log.warn("Invalid status received: {}", request.getStatus());
        }
        return serviceResponse;
    }

//    for scheduler to update

    //    TODO : for make sure the updating proper room we will need the initiated by field as well
    @Transactional
    public Map<String, String> updateStatus(Status s1, Status s2, String roomHash) {
        log.info("Updating confess status from {} to {} for room hash: {}", s1, s2, roomHash);
        Optional<Confess> availableRequest = repo.findByRoomHash(roomHash);

        Map<String, String> response = new HashMap<>();
        if (availableRequest.isEmpty()) {
            response.put("error", "No Data is available");
            log.warn("No confess data found for room hash: {}", roomHash);
            return response;
        }
//        TODO :  need to find the failure case.
        if (availableRequest.get().getStatus().equals(s1)) {
            availableRequest.get().setStatus(s2);
            try{
                Confess updatedConfess = saveConfessInDbWithRetry(availableRequest.get());
                log.info("Confess status updated successfully from {} to {} for id: {}",
                        s1, s2, updatedConfess.getId());
                response.put("saved", "data saved successfully");
            }catch(Exception e){
                log.error("Error updating confess status: {}", e.getMessage(), e);
                response.put("error", "Failed to save data");
            }
        }else{
            log.warn("Current status {} does not match expected status {} for room hash: {}",
                    availableRequest.get().getStatus(), s1, roomHash);
        }
        return response;
    }

    public Optional<List<ConfessDto>> getAllRequestForUser(String initiatedBy) {
        log.debug("Fetching all confess requests for user: {}", initiatedBy);
        List<ConfessDto> requests = repo.findByInitiatedBy(initiatedBy)
                .stream()
                .map(this::toDtoConvert)
                .toList();
        log.info("Retrieved {} confess requests for user: {}", requests.size(), initiatedBy);
        return Optional.of(requests);
    }

    public Optional<List<ConfessDto>> getAllInProgressRequest(Status status) {
        log.debug("Fetching all confess requests with status: {}", status);
        List<ConfessDto> requests = repo.findByStatus(status)
                .stream()
                .map(this::toDtoConvert)
                .toList();
        log.info("Retrieved {} confess requests with status: {}", requests.size(), status);
        return Optional.of(requests);
    }

    private ConfessDto toDtoConvert(Confess c){
        ConfessDto dto = new ConfessDto();
        dto.setInitiatedBy(c.getInitiatedBy());
        dto.setSenderOriginalName(c.getSenderOriginalName());
        dto.setSenderEmail(c.getSenderEmail());
        dto.setRoomName(c.getRoomName());
        dto.setRoomHash(c.getRoomHash());
        dto.setReceiverAlias(c.getReceiverAlias());
        dto.setConfessType(c.getConfessType());
        dto.setEmail(c.getEmail());
        dto.setSongName(c.getSongName());
        dto.setSingerName(c.getSingerName());
        dto.setMessage(c.getMessage());
        dto.setStatus(c.getStatus());
        dto.setNote(c.getNote());

        return dto;
    }

    public Optional<Confess> getDetailsByRoomHash(String roomHash) {
        log.debug("Fetching confess details by room hash: {}", roomHash);
        Optional<Confess> confess = repo.findByRoomHash(roomHash);
        if(confess.isPresent()){
            log.debug("Found confess data for room hash: {}", roomHash);
        }else{
            log.warn("No confess data found for room hash: {}", roomHash);
        }
        return confess;
    }


}
