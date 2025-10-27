package com.music.musicwebapplication.controller;

import com.music.musicwebapplication.entity.Room;
import com.music.musicwebapplication.repo.RoomRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * ChatController handles real-time messaging and participant updates
 * via WebSocket using STOMP protocol
 */
@Controller
@Slf4j
@RequiredArgsConstructor
public class ChatController {

    private final SimpMessagingTemplate messagingTemplate;
    private final RoomRepo roomRepo;

    /**
     * Handle chat messages and broadcast to all users in the room
     *
     * @param roomName - The room identifier
     * @param message - Chat message payload
     * @param sessionAttributes - User session data
     * @return The message with sender info added
     */
    @MessageMapping("/chat/{roomName}/send")
    @SendTo("/topic/chat/{roomName}")
    public Map<String, Object> handleChatMessage(
            @DestinationVariable String roomName,
            @Payload Map<String, Object> message,
            @Header(value = "simpSessionAttributes", required = false) Map<String, Object> sessionAttributes) {

        String username = extractUsername(sessionAttributes);
        log.info("💬 Chat message from user: {} in room: {}", username, roomName);

        message.put("sender", username);
        message.put("type", "CHAT");
        message.put("timestamp", System.currentTimeMillis());

        return message;
    }

    /**
     * Handle user join event and broadcast updated participant list
     *
     * @param roomName - The room identifier
     * @param message - Join notification payload
     * @param sessionAttributes - User session data
     */
    @MessageMapping("/chat/{roomName}/addUser")
    public void handleUserJoin(
            @DestinationVariable String roomName,
            @Payload Map<String, Object> message,
            @Header(value = "simpSessionAttributes", required = false) Map<String, Object> sessionAttributes) {

        String username = extractUsername(sessionAttributes);
        log.info("👤 User {} joined room: {}", username, roomName);

        try {
            // Broadcast join message to all users
            messagingTemplate.convertAndSend("/topic/chat/" + roomName, message);

            // Broadcast updated participant list
            broadcastParticipants(roomName);

            log.debug("✅ User join broadcast completed for room: {}", roomName);
        } catch (Exception e) {
            log.error("❌ Error handling user join in room {}: {}", roomName, e.getMessage(), e);
        }
    }

    /**
     * Handle user leave event and broadcast updated participant list
     *
     * @param roomName - The room identifier
     * @param message - Leave notification payload
     * @param sessionAttributes - User session data
     */
    @MessageMapping("/chat/{roomName}/removeUser")
    public void handleUserLeave(
            @DestinationVariable String roomName,
            @Payload Map<String, Object> message,
            @Header(value = "simpSessionAttributes", required = false) Map<String, Object> sessionAttributes) {

        String username = extractUsername(sessionAttributes);
        log.info("👋 User {} left room: {}", username, roomName);

        try {
            // Broadcast leave message to all users
            messagingTemplate.convertAndSend("/topic/chat/" + roomName, message);

            // Broadcast updated participant list
            broadcastParticipants(roomName);

            log.debug("✅ User leave broadcast completed for room: {}", roomName);
        } catch (Exception e) {
            log.error("❌ Error handling user leave in room {}: {}", roomName, e.getMessage(), e);
        }
    }

    /**
     * Broadcast current participants list to all users in the room
     * This ensures all clients have up-to-date participant information
     *
     * @param roomName - The room identifier
     */
    @Transactional(readOnly = true)
    protected void broadcastParticipants(String roomName) {
        try {
            Room room = roomRepo.findRoomByRoomName(roomName)
                    .orElse(null);

            if (room != null && room.getParticipant() != null && !room.getParticipant().isEmpty()) {
                log.info("📡 Broadcasting {} participants for room: {}",
                        room.getParticipant().size(), roomName);

                messagingTemplate.convertAndSend(
                        "/topic/chat/" + roomName + "/participants",
                        room.getParticipant()
                );
            } else {
                log.warn("⚠️ Room not found or has no participants: {}", roomName);
            }
        } catch (Exception e) {
            log.error("❌ Error broadcasting participants for room {}: {}",
                    roomName, e.getMessage(), e);
        }
    }

    /**
     * Extract username from session attributes safely
     *
     * @param sessionAttributes - Map containing session data
     * @return Username if found, "Unknown" otherwise
     */
    private String extractUsername(Map<String, Object> sessionAttributes) {
        if (sessionAttributes == null) {
            return "Unknown";
        }

        Object username = sessionAttributes.get("username");
        return username != null ? (String) username : "Unknown";
    }
}