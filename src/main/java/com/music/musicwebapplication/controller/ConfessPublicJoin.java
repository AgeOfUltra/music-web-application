package com.music.musicwebapplication.controller;

import com.music.musicwebapplication.dto.NodeLogin;
import com.music.musicwebapplication.entity.Confess;
import com.music.musicwebapplication.exception.ConfessRoomException;
import com.music.musicwebapplication.service.ConfessService;
import com.music.musicwebapplication.enums.Status;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@Slf4j
@Controller
@RequestMapping("/app/music/nodes")
public class ConfessPublicJoin {
    private final ConfessService service ;

    public ConfessPublicJoin(ConfessService service) {
        this.service = service;
    }


//    this is external link url : /app/music/node/join?sender=jarvis&roomId=abcdef
    @GetMapping("/join")
    public String displayJoinRoom(@RequestParam(required = false) String sender, @RequestParam(required = false) String roomId,@RequestParam(required = false) String error, Model model){

        if(sender.isBlank() || roomId.isBlank()){
            throw new ConfessRoomException("Oops! invalid request");
        }

        Optional<Confess> request = service.getDetailsByRoomHash(roomId);
        if(request.isEmpty()){
            throw new ConfessRoomException("Oops your room not found");
        }
        Confess confess = request.get();
        if(confess.getStatus() == Status.EXPIRED){
            throw new ConfessRoomException("feelings expired");
        }
       if(confess.getSenderOriginalName().equals(sender) && confess.getStatus() == Status.SENT){
           model.addAttribute("sender",sender);
           model.addAttribute("roomId",roomId);
           model.addAttribute("nodeLogin",new NodeLogin());
           return "passcode";
       }else{ // otherwise in reading status then also don't work
           throw new ConfessRoomException("Oops! invalid request");
       }

    }
//    url to handle the after passcode enters
    @PostMapping("/connect/node")
    public String connectFeelings(@ModelAttribute("nodeLogin") NodeLogin node, Model model){

        log.info("Sent data {}",node);
        Optional<Confess> confessData = service.getDetailsByRoomHash(node.getRoomId());

        if(confessData.isEmpty()){
            throw new ConfessRoomException("Oops! invalid request");
        }

        Confess confess = confessData.get();
//        validate the login details
        if(!node.getPasscode().equals(confess.getPasscode()) || !confess.getSenderOriginalName().equals(node.getSender())){
            throw new ConfessRoomException("Oops! invalid request and credentials");
        }

        if(confess.getStatus()== Status.DONE || confess.getStatus()== Status.EXPIRED){
            throw new ConfessRoomException("Link expired or already consumed the content");
        }
//        and need to set the update
           Map<String,String> response = service.updateStatus(Status.SENT,Status.READING, node.getRoomId());

//        here last modified date will be updated as current time stamp

        if(response.containsKey("error")){
            throw new ConfessRoomException("Oops ! Join failed due to internal error");
        }
        else{
            log.info("Status updated from sent to reading");
        }

//        need to get the data of the song and then message

        log.info("confess data {}",confess);
        model.addAttribute("receiverName",confess.getReceiverAlias());
        model.addAttribute("roomId",confess.getRoomHash());
        model.addAttribute("songFileName",confess.getSongName());
        model.addAttribute("message",confess.getMessage());


//        and display the page
        return "confess";

    }
    @PostMapping("/confess/complete")
    public String completeConfession(@RequestParam String roomId) {
        if(roomId.isBlank()){
            throw new ConfessRoomException("Oops! invalid request");
        }
        log.info("room hash {}",roomId);
        // Update confession status in database
        Map<String, String> response = service.updateStatus(Status.READING,Status.DONE,roomId);

        if(response.containsKey("error")){
            throw new ConfessRoomException("Oops ! Join failed due to internal error");
        }
        else{
            log.info("Status updated from reading  to done");
        }

        return "redirect:/app/music/nodes/connect/finish";
    }

    @GetMapping("/connect/finish")
    public String showFinishPage() {
        return "finish"; // Returns finish.html template
    }
}
