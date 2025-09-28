package com.music.musicwebapplication.controller;

import com.music.musicwebapplication.dto.RoomDto;
import com.music.musicwebapplication.entity.Room;
import com.music.musicwebapplication.service.RoomService;
import org.aspectj.apache.bcel.classfile.Module;
import org.modelmapper.ModelMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/app/music")
public class ChatController {
    private final RoomService rService;
    private final ModelMapper mapper;

    public ChatController(RoomService rService, ModelMapper mapper){

        this.rService = rService;
        this.mapper = mapper;
    }

    @GetMapping("/chat")
    public String chatRoom(@RequestParam(required = false) String roomId,
                           Authentication authentication,
                           Model model) {
        model.addAttribute("username", authentication.getName());
        model.addAttribute("roomId", roomId != null ? roomId : "general");

        return "chat";
    }


    public ResponseEntity<RoomDto> createRoom(@RequestParam String roomId, @RequestParam int maxCount, Authentication authentication ){
        Room room = rService.createRoom(roomId,maxCount, authentication.getName(), true);
        return ResponseEntity.ok(mapper.map(room, RoomDto.class));

    }
}