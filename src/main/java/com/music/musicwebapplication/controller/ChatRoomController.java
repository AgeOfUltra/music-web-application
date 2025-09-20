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
public class ChatRoomController {

    @MessageMapping("/chat/{roomId}/send")
    @SendTo("/topic/chat/{roomId}")
    public ChatMessage sendMessage(@DestinationVariable String roomId, @Payload ChatMessage message) {
        log.info("Message sent to room {}: {} by {}", roomId, message.getContent(), message.getSender());
        // Ensure roomId is set in the message
        message.setRoomId(roomId);
        return message;
    }

    @MessageMapping("/chat/{roomId}/addUser")
    @SendTo("/topic/chat/{roomId}")
    public ChatMessage addUser(@DestinationVariable String roomId,
                               @Payload ChatMessage chatMessage,
                               SimpMessageHeaderAccessor headerAccessor) {

        // Store user info in session attributes for disconnect handling
        Objects.requireNonNull(headerAccessor.getSessionAttributes()).put("username", chatMessage.getSender());
        headerAccessor.getSessionAttributes().put("roomId", roomId);

        log.info("User {} joined room: {}", chatMessage.getSender(), roomId);

        // Ensure message has correct roomId
        chatMessage.setRoomId(roomId);

        return chatMessage;
    }

    @MessageMapping("/chat/{roomId}/removeUser")
    @SendTo("/topic/chat/{roomId}")
    public ChatMessage removeUser(@DestinationVariable String roomId,
                                  @Payload ChatMessage chatMessage,
                                  SimpMessageHeaderAccessor headerAccessor) {

        log.info("User {} left room: {}", chatMessage.getSender(), roomId);

        // Ensure message has correct roomId
        chatMessage.setRoomId(roomId);

        return chatMessage;
    }
}