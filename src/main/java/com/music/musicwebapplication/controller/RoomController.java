package com.music.musicwebapplication.controller;

import com.music.musicwebapplication.entity.Participant;
import com.music.musicwebapplication.entity.Room;
import com.music.musicwebapplication.service.RoomService;
import org.modelmapper.ModelMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;


@Controller
@RequestMapping("/app/music")
public class RoomController {
    private final RoomService rService;
    private final ModelMapper mapper;

    public RoomController(RoomService rService, ModelMapper mapper){

        this.rService = rService;
        this.mapper = mapper;
    }

    @GetMapping("/chat")
    public String chatRoom(@RequestParam(required = false) String roomName,
                           Authentication authentication,
                           Model model) {
        model.addAttribute("username", authentication.getName());
        model.addAttribute("roomName", roomName);

        return "chat";
    }


    @PostMapping("/room/create")
//    public ResponseEntity<Room> createRoom(@RequestParam String roomName, @RequestParam int maxCount, Authentication authentication ){
    public ResponseEntity<Room> createRoom(@RequestParam String roomName, @RequestParam int maxCount, @RequestParam String username ){
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
    public ResponseEntity<Participant> joinRoom(@RequestParam String roomName,@RequestParam String username){
        Participant newParticipant = new Participant();
        newParticipant.setUserName(username);
        newParticipant.setOrganizer(false);
        Participant participant = rService.joinRoom(roomName,newParticipant);
        return ResponseEntity.ok(participant);
    }

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

    @GetMapping("/room/getAvailability")
    public ResponseEntity<Integer> getAvailableCount(@RequestParam String roomName){
        Room room = rService.getRoomDetails(roomName);
        int availableCount = room.getMaxCount() - room.getParticipant().size();
        return ResponseEntity.ok(availableCount);
    }
}