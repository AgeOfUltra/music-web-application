package com.music.musicwebapplication.utils;

import com.music.musicwebapplication.dto.ChatMessage;
import com.music.musicwebapplication.support.MessageType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Component
@Slf4j
@RequiredArgsConstructor
public class WebSocketEventListener {

    private final SimpMessagingTemplate messageTemplate;

    @EventListener
    public void handleWebSocketDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String username = (String) headerAccessor.getSessionAttributes().get("username");
        String roomId = (String) headerAccessor.getSessionAttributes().get("roomId");

        if (username != null) {
            log.info("User {} disconnected from room: {}", username, roomId != null ? roomId : "unknown");

            String targetRoom = roomId != null ? roomId : "general";

            var messageContext = ChatMessage.builder()
                    .type(MessageType.LEAVE)
                    .sender(username)
                    .content(username + " left the chat")
                    .roomId(targetRoom)
                    .build();

            try {
                messageTemplate.convertAndSend("/topic/chat/" + targetRoom, messageContext);
                log.info("Leave message sent for user {} in room {}", username, targetRoom);
            } catch (Exception e) {
                log.error("Failed to send leave message for user {} in room {}: {}",
                        username, targetRoom, e.getMessage());
            }
        }
    }
}