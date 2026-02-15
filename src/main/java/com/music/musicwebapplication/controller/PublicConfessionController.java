package com.music.musicwebapplication.controller;

import com.music.musicwebapplication.dto.NodeLogin;
import com.music.musicwebapplication.entity.Confess;
import com.music.musicwebapplication.exception.ConfessRoomException;
import com.music.musicwebapplication.service.ConfessService;
import com.music.musicwebapplication.enums.Status;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@Slf4j
@Controller
@RequestMapping("/app/music/nodes")
public class PublicConfessionController {
    private final ConfessService service ;


    public PublicConfessionController(ConfessService service) {
        this.service = service;
        log.debug("PublicConfessionController initialized");
    }


    //    this is external link url : /app/music/node/join?sender=jarvis&roomId=abcdef
    @GetMapping("/join")
    public String displayJoinRoom(@RequestParam(required = false) String sender, @RequestParam(required = false) String roomId,@RequestParam(required = false) String error, Model model){
        log.info("Join room request received - sender: {}, roomId: {}", sender, roomId);

        if(sender.isBlank() || roomId.isBlank()){
            log.warn("Invalid join request - blank sender or roomId");
            throw new ConfessRoomException("Oops! invalid request");
        }

        Optional<Confess> request = service.getDetailsByRoomHash(roomId);
        if(request.isEmpty()){
            log.warn("Room not found for roomId: {}", roomId);
            throw new ConfessRoomException("Oops your room not found");
        }

        Confess confess = request.get();
        log.debug("Confess data retrieved for roomId: {}, status: {}", roomId, confess.getStatus());

        if(confess.getStatus() == Status.EXPIRED){
            log.info("Join attempt failed - room expired for roomId: {}", roomId);
            throw new ConfessRoomException("feelings expired");
        }

        if(confess.getSenderOriginalName().equals(sender) && confess.getStatus() == Status.SENT){
            log.info("Valid join request - displaying passcode page for sender: {}, roomId: {}", sender, roomId);
            model.addAttribute("sender",sender);
            model.addAttribute("roomId",roomId);
            model.addAttribute("nodeLogin",new NodeLogin());
            return "passcode";
        }else{ // otherwise in reading status then also don't work
            log.warn("Invalid join request - sender mismatch or invalid status. Sender: {}, Status: {}", sender, confess.getStatus());
            throw new ConfessRoomException("Oops! invalid request");
        }

    }

    //    url to handle the after passcode enters
    @PostMapping("/connect/node")
    public String connectFeelings(@ModelAttribute("nodeLogin") NodeLogin node, Model model){
        log.info("Connect feelings request received for roomId: {}, sender: {}", node.getRoomId(), node.getSender());
        log.debug("NodeLogin data: {}", node);

        Optional<Confess> confessData = service.getDetailsByRoomHash(node.getRoomId());

        if(confessData.isEmpty()){
            log.warn("Connect failed - room not found for roomId: {}", node.getRoomId());
            throw new ConfessRoomException("Oops! invalid request");
        }

        Confess confess = confessData.get();
        log.debug("Confess retrieved - status: {}, sender: {}", confess.getStatus(), confess.getSenderOriginalName());

//        validate the login details
        if(!node.getPasscode().equals(confess.getPasscode()) || !confess.getSenderOriginalName().equals(node.getSender())){
            log.warn("Connect failed - invalid credentials for roomId: {}, sender: {}", node.getRoomId(), node.getSender());
            throw new ConfessRoomException("Oops! invalid request and credentials");
        }

        if(confess.getStatus()== Status.DONE || confess.getStatus()== Status.EXPIRED){
            log.info("Connect failed - link expired or consumed for roomId: {}, status: {}", node.getRoomId(), confess.getStatus());
            throw new ConfessRoomException("Link expired or already consumed the content");
        }

//        and need to set the update
        log.debug("Updating status from SENT to READING for roomId: {}", node.getRoomId());
        Map<String,String> response = service.updateStatus(Status.SENT,Status.READING, node.getRoomId());

//        here last modified date will be updated as current time stamp

        if(response.containsKey("error")){
            log.error("Status update failed for roomId: {}, error: {}", node.getRoomId(), response.get("error"));
            throw new ConfessRoomException("Oops ! Join failed due to internal error");
        }
        else{
            log.info("Status updated successfully from SENT to READING for roomId: {}", node.getRoomId());
        }

//        need to get the data of the song and then message

        log.debug("Preparing confess page with data - receiver: {}, song: {}", confess.getReceiverAlias(), confess.getSongName());
        model.addAttribute("receiverName",confess.getReceiverAlias());
        model.addAttribute("roomId",confess.getRoomHash());
        model.addAttribute("songFileName",confess.getSongName());
        model.addAttribute("message",confess.getMessage());
        model.addAttribute("token",confess.getToken());
        model.addAttribute("roomName",confess.getRoomName());


//        and display the page
        return "confess";

    }

    @PostMapping("/confess/complete")
    public ResponseEntity<Void> completeConfession(@RequestParam String roomId) {
        log.info("Complete confession request received for roomId: {}", roomId);

        if(roomId.isBlank()){
            log.warn("Complete confession failed - blank roomId");
            throw new ConfessRoomException("Oops! invalid request");
        }

        log.debug("Updating status from READING to DONE for roomId: {}", roomId);
        // Update confession status in database
        Map<String, String> response = service.updateStatus(Status.READING,Status.DONE,roomId);

        if(response.containsKey("error")){
            log.error("Status update failed for roomId: {}, error: {} while confess reading finished", roomId, response.get("error"));
            throw new ConfessRoomException("Oops ! Join failed due to internal error");
        }
        else{
            log.info("Status updated successfully from READING to DONE for roomId: {}", roomId);
        }

        return ResponseEntity.status(HttpStatus.OK).build();
    }

}
