package com.music.musicwebapplication.controller;

import com.music.musicwebapplication.entity.Participant;
import com.music.musicwebapplication.entity.Room;
import com.music.musicwebapplication.service.RoomService;
import com.music.musicwebapplication.utils.ColorUsageUtil;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Controller
@RequestMapping("/app/music")
public class RoomController {
    private final RoomService rService;
    private final PublicSongController publicSongController;
    private final ColorUsageUtil colorUsageUtil;
    private final SimpMessagingTemplate messagingTemplate;
    public RoomController(RoomService rService, PublicSongController publicSongController, ColorUsageUtil colorUsageUtil, SimpMessagingTemplate messagingTemplate){

        this.rService = rService;
        this.publicSongController = publicSongController;
        this.colorUsageUtil = colorUsageUtil;
        this.messagingTemplate = messagingTemplate;
    }

    @GetMapping("/chat")
    public String chatRoom(@RequestParam String roomName,
                           Authentication authentication,
                           Model model, HttpSession session) {
        model.addAttribute("username", authentication.getName());
        model.addAttribute("roomName", roomName);
        model.addAttribute("ALLSONGS",publicSongController.getAllSongs());
        model.addAttribute("roomCount", currentParticipantCount(roomName));
        model.addAttribute("totalCount", rService.getRoomDetails(roomName).getMaxCount());
        model.addAttribute("participants", getParticipants(roomName));
        model.addAttribute("userColor",colorUsageUtil.getUserColors(authentication.getName()).get("userColor"));
        model.addAttribute("darkerColor",colorUsageUtil.getUserColors(authentication.getName()).get("darkerColor"));
        model.addAttribute("jwtToken",session.getAttribute("jwtToken"));
        model.addAttribute("isOrganizer",rService.isUserOrganizer(roomName,authentication.getName()));
        return "chat";
    }

//        Actual method
    @PostMapping("/room/create")
    public ModelAndView createRoom(@RequestParam String roomName, @RequestParam int maxCount, @RequestParam String username ) {

        ResponseEntity<?> response= createRoomApi(roomName, maxCount, username);
        if(response.getStatusCode().equals(HttpStatus.OK)){
            return new ModelAndView("redirect:/app/music/chat?roomName="+roomName);
        }else{
            return new ModelAndView("error").addObject("message", "Room creation failed");
        }

    }
//    API
    private ResponseEntity<Room> createRoomApi(String roomName,int maxCount,String username ){
        Room roomBuild = new Room();
        roomBuild.setRoomName(roomName);
        roomBuild.setMaxCount(maxCount);
        Participant participant = new Participant();
        participant.setUserName(username);
        participant.setOrganizer(true);
        List<Participant> participants = new ArrayList<>();
        participants.add(participant);
        roomBuild.setParticipant(participants);
        Room room = rService.createRoom(roomBuild);
        return ResponseEntity.ok(room);

    }

    @PostMapping("/room/join")
//    Actual method for join room
    public ModelAndView joinRoom(@RequestParam String roomName, @RequestParam String username) {
        ResponseEntity<?> response = joinRoomApi(roomName, username);
        if (response.getStatusCode().equals(HttpStatus.OK)) {
            // Notify all users in the room about the new participant
            broadcastParticipantUpdate(roomName);
            return new ModelAndView("redirect:/app/music/chat?roomName=" + roomName);
        } else {
            return new ModelAndView("error").addObject("message", "Room Login Failed");
        }
    }

    private void broadcastParticipantUpdate(String roomName) {
        try {
            Room room = rService.getRoomDetails(roomName);
            messagingTemplate.convertAndSend(
                    "/topic/chat/" + roomName + "/participants",
                    room.getParticipant()
            );
        } catch (Exception e) {
            log.error("Error broadcasting participant update", e);
        }
    }


//    API
    private ResponseEntity<Participant> joinRoomApi(String roomName,String username){
        Participant newParticipant = new Participant();
        newParticipant.setUserName(username);
        newParticipant.setOrganizer(false);
        Participant participant = rService.joinRoom(roomName,newParticipant);
        return ResponseEntity.ok(participant);
    }

    //upon logout or participant leave from the room
    @DeleteMapping("/room/leave")
    public ResponseEntity<Boolean> leaveRoom(@RequestParam String roomName, @RequestParam String username){
        boolean isLeft = rService.exitFromRoom(roomName,username);
        return ResponseEntity.ok(isLeft);
    }

    @GetMapping("/room/getRoom")
    public ResponseEntity<Room> getRoomInformation(@RequestParam String roomName){
        Room room = rService.getRoomDetails(roomName);
        return ResponseEntity.ok(room);

    }

    private int getAvailableParticipants(String roomName){
        ResponseEntity<Integer> participants = getAvailableCount(roomName);
        if(participants.getStatusCode().equals(HttpStatus.OK) && participants.getBody() != null){
            return participants.getBody();

        }
        return 0;
    }

//    @GetMapping("/room/getAvailability")
    private ResponseEntity<Integer> getAvailableCount(String roomName){
        Room room = rService.getRoomDetails(roomName);
        int availableCount = room.getMaxCount() - room.getParticipant().size();
        return ResponseEntity.ok(availableCount);
    }

    private int currentParticipantCount(String roomName){
        ResponseEntity<Integer> participants = currentParticipantCountApi(roomName);
        if(participants.getStatusCode().equals(HttpStatus.OK) && participants.getBody() != null){
            return participants.getBody();
        }
        return 0;
    }

    private ResponseEntity<Integer> currentParticipantCountApi(String roomName){

        Room room = rService.getRoomDetails(roomName);
        int availableCount =  room.getParticipant().size();
        return ResponseEntity.ok(availableCount);
    }

    private List<Participant> getParticipants(String roomName){
        ResponseEntity<List<Participant>> participants = getAllParticipants(roomName);
        List<Participant> availableParticipants = null;
        if(participants.getStatusCode().equals(HttpStatus.OK)){
            availableParticipants = participants.getBody();

        }
        return availableParticipants;
    }

//    API to get all participants in a room
    @GetMapping("/room/getAllParticipants")
    private ResponseEntity<List<Participant>> getAllParticipants(String roomName){
        Room room = rService.getRoomDetails(roomName);
        List<Participant> participants = room.getParticipant();
        return ResponseEntity.ok(participants);
    }



}