package com.music.musicwebapplication.controller;

import com.music.musicwebapplication.entity.Room;
import com.music.musicwebapplication.service.RoomService;
import org.modelmapper.ModelMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;


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
    public ResponseEntity<Room> createRoom(@RequestParam String roomName, @RequestParam int maxCount, Authentication authentication ){
        Room room = rService.createRoom(roomName,maxCount, authentication.getName(), true);
        return ResponseEntity.ok(room);

    }

    @PostMapping("/room/join")
    public ResponseEntity<Room> joinRoom(@RequestParam String roomName,Authentication authentication){
        Room room = rService.joinRoom(roomName,authentication.getName(),false);
        return ResponseEntity.ok(room);
    }

    @DeleteMapping("/room/leave")
    public ResponseEntity<Boolean> leaveRoom(@RequestParam String roomName, Authentication authentication){
        boolean isLeft = rService.exitFromRoom(roomName,authentication.getName());
        return ResponseEntity.ok(isLeft);
    }

    @GetMapping("/room/getRoom")
    public ResponseEntity<Room> getRoomInformation(@RequestParam String roomName){
        Room room = rService.getRoomDetails(roomName);
        return ResponseEntity.ok(room);

    }
}