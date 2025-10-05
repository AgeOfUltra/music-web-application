package com.music.musicwebapplication.controller;

import com.music.musicwebapplication.dto.PlaybackMessage;
import com.music.musicwebapplication.exception.RoomNotFoundException;
import com.music.musicwebapplication.repo.RoomRepo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.AccessDeniedException;
import java.util.Map;

@Controller
@Slf4j
public class PlaybackController {

    @Autowired
    private final SimpMessagingTemplate messagingTemplate;
    private final RoomRepo repo ;

    public PlaybackController(SimpMessagingTemplate messagingTemplate, RoomRepo repo) {
        this.messagingTemplate = messagingTemplate;
        this.repo = repo;
    }

    @MessageMapping("/chat/{roomName}/playback")
    @Transactional(readOnly = true)
    public void handlePlayback(@DestinationVariable String roomName,
                               @Payload Map<String, Object> message,
                               @Header("simpSessionAttributes") Map<String, Object> sessionAttributes) throws AccessDeniedException {

        String username = (String) sessionAttributes.get("username");

        boolean isOrganizer = repo.findRoomByRoomName(roomName)
                .orElseThrow(()-> new RoomNotFoundException("Room Not found"))
                .getParticipant().stream().anyMatch(u -> u.getUserName().equals(username) && u.isOrganizer());

        if(!isOrganizer){
            throw new AccessDeniedException("Only organizer can control PlayBack");
        }

        message.put("controller",username);

        // Broadcast to all users in the room
        messagingTemplate.convertAndSend("/topic/chat/" + roomName + "/playback", message);

        log.info("Broadcasting {} action in room {} by {}",
                message.get("action"), roomName, username);
    }
}
