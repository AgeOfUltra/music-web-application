package com.music.musicwebapplication.controller;

import com.music.musicwebapplication.dto.ChatMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

import java.util.Objects;

@Controller
@Slf4j
public class ChatController {

    @MessageMapping("/chat/{roomName}/send")
    @SendTo("/topic/chat/{roomName}")
    public ChatMessage sendMessage(@DestinationVariable String roomName, @Payload ChatMessage message) {
        log.info("Message sent to room {}: {} by {}", roomName, message.getContent(), message.getSender());
        message.setRoomName(roomName);
        return message;
    }

    @MessageMapping("/chat/{roomName}/addUser")
    @SendTo("/topic/chat/{roomName}")
    public ChatMessage addUser(@DestinationVariable String roomName,
                               @Payload ChatMessage chatMessage,
                               SimpMessageHeaderAccessor headerAccessor) {

        Objects.requireNonNull(headerAccessor.getSessionAttributes()).put("username", chatMessage.getSender());
        headerAccessor.getSessionAttributes().put("roomId", roomName);

        log.info("User {} joined room: {}", chatMessage.getSender(), roomName);

        chatMessage.setRoomName(roomName);

        return chatMessage;
    }

    @MessageMapping("/chat/{roomId}/removeUser")
    @SendTo("/topic/chat/{roomId}")
    public ChatMessage removeUser(@DestinationVariable String roomName,
                                  @Payload ChatMessage chatMessage) {

        log.info("User {} left room: {}", chatMessage.getSender(), roomName);
        chatMessage.setRoomName(roomName);

        return chatMessage;
    }
}