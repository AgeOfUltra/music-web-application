package com.music.musicwebapplication.utils;

import com.music.musicwebapplication.dto.ChatMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
public class WebSocketEventListener {

    private final SimpMessagingTemplate messageTemplate;

    @EventListener
    public void handleWebSocketDisconnect(SessionDisconnectEvent event) {
        try {
            StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());

            if (headerAccessor.getSessionAttributes() == null) {
                log.warn("⚠️ Session attributes missing in disconnect event");
                return;
            }

            Map<String, Object> sessionAttributes = headerAccessor.getSessionAttributes();
            String username = (String) sessionAttributes.get("username");
            String roomName = (String) sessionAttributes.get("roomName");

            // Not enough info — stop
            if (username == null || roomName == null) {
                return;
            }

            // ⚠️ IMPORTANT: Do NOT send LEAVE here.
            log.info("🔌 WebSocket disconnected for user {} in room {}, but NOT treating as leave.", username, roomName);

        } catch (Exception e) {
            log.error("❌ Error handling WebSocket disconnect: {}", e.getMessage(), e);
        }
    }


    /**
     * Send disconnect/leave message to room
     */
    private void sendDisconnectMessage(String username, String roomName) {
        try {
            // Create leave message
            ChatMessage leaveMessage = new ChatMessage();
            leaveMessage.setSender(username);
            leaveMessage.setType("LEAVE");
            leaveMessage.setContent(username + " left the chat");
            leaveMessage.setTimestamp(System.currentTimeMillis());

            // Broadcast to room
            messageTemplate.convertAndSend(
                    "/topic/chat/" + roomName,
                    leaveMessage
            );

            log.info("✅ Leave message sent for user {} in room {}", username, roomName);

        } catch (Exception e) {
            log.error("❌ Failed to send leave message for user {} in room {}: {}",
                    username, roomName, e.getMessage(), e);
        }
    }
}