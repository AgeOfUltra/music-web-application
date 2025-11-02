package com.music.musicwebapplication.controller;

import com.music.musicwebapplication.dto.ChatMessage;
import com.music.musicwebapplication.dto.PlaybackMessage;
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

@Controller
@Slf4j
@RequiredArgsConstructor
public class ChatController {

    private final SimpMessagingTemplate messagingTemplate;
    private final RoomRepo roomRepo;

    // ==================== CHAT MESSAGING ====================
    @MessageMapping("/chat/{roomName}/send")
    public void handleChatMessage(
            @DestinationVariable String roomName,
            @Payload ChatMessage message,
            @Header(value = "simpSessionAttributes", required = false) Map<String, Object> sessionAttributes) {

        try {
            // Use sender from message if available, otherwise try to extract from session
            String username = null;

            if (message.getSender() != null && !message.getSender().isEmpty()) {
                username = message.getSender();
                log.info("✅ Got username from message sender: {}", username);
            } else {
                username = extractUsername(sessionAttributes);
                if (username != null) {
                    log.info("✅ Got username from session: {}", username);
                }
            }

            if (username == null || username.isEmpty()) {
                log.error("❌ Username not found in message or session");
                return;
            }

            message.setSender(username);
            message.setType("CHAT");
            message.setTimestamp(System.currentTimeMillis());

            log.info("💬 Chat message from: {} in room: {}", username, roomName);
            log.info("📤 Broadcasting message: {}", message);

            messagingTemplate.convertAndSend("/topic/chat/" + roomName, message);

        } catch (Exception e) {
            log.error("❌ Error handling chat message in room {}: {}", roomName, e.getMessage(), e);
        }
    }

    // ==================== PLAYBACK CONTROL ====================
    @MessageMapping("/chat/{roomName}/playback")
    public void handlePlaybackCommand(
            @DestinationVariable String roomName,
            @Payload PlaybackMessage playbackMessage,
            @Header(value = "simpSessionAttributes", required = false) Map<String, Object> sessionAttributes) {

        try {
            log.info("📥 Received playback message: {}", playbackMessage);

            // Extract username from message (sent by client)
            String username = null;

            // Try to get from message sender field
            if (playbackMessage != null && playbackMessage.getSender() != null && !playbackMessage.getSender().isEmpty()) {
                username = playbackMessage.getSender();
                log.info("✅ Got username from message sender: {}", username);
            }
            // Fallback to controller field
            else if (playbackMessage != null && playbackMessage.getController() != null && !playbackMessage.getController().isEmpty()) {
                username = playbackMessage.getController();
                log.info("✅ Got username from message controller: {}", username);
            }
            // Fallback to session attributes
            else if (sessionAttributes != null) {
                username = extractUsername(sessionAttributes);
                log.info("✅ Got username from session: {}", username);
            }

            // Validate username
            if (username == null || username.isEmpty()) {
                log.error("❌ Username not found - Message: {}, Session: {}", playbackMessage, sessionAttributes);
                sendErrorMessage(roomName, "Username not found in session");
                return;
            }

            // Validate action
            if (playbackMessage.getAction() == null || playbackMessage.getAction().isEmpty()) {
                log.warn("⚠️ Missing action in playback message for room: {}", roomName);
                return;
            }

            // Ensure controller and sender are set
            playbackMessage.setController(username);
            playbackMessage.setSender(username);

            log.info("🎵 Playback action: {} by: {} in room: {}",
                    playbackMessage.getAction(), username, roomName);
            log.info("📤 Broadcasting playback message: {}", playbackMessage);

            // Broadcast to all users in the room
            messagingTemplate.convertAndSend("/topic/chat/" + roomName + "/playback", playbackMessage);

        } catch (Exception e) {
            log.error("❌ Error handling playback command in room {}: {}", roomName, e.getMessage(), e);
            sendErrorMessage(roomName, "Error processing playback command: " + e.getMessage());
        }
    }

    // ==================== USER JOIN ====================
    @MessageMapping("/chat/{roomName}/addUser")
    public void handleUserJoin(
            @DestinationVariable String roomName,
            @Payload ChatMessage message,
            @Header(value = "simpSessionAttributes", required = false) Map<String, Object> sessionAttributes) {

        try {
            // Use sender from message if available, otherwise extract from session
            String username = null;

            if (message.getSender() != null && !message.getSender().isEmpty()) {
                username = message.getSender();
                log.info("✅ Got username from message sender: {}", username);
            } else {
                username = extractUsername(sessionAttributes);
                if (username != null) {
                    log.info("✅ Got username from session: {}", username);
                }
            }

            if (username == null || username.isEmpty()) {
                log.error("❌ Username not found for user join");
                return;
            }

            message.setSender(username);
            message.setType("JOIN");

            log.info("👤 User {} joined room: {}", username, roomName);

            // Broadcast join message
            messagingTemplate.convertAndSend("/topic/chat/" + roomName, message);

            // Broadcast updated participant list
            broadcastParticipants(roomName);

        } catch (Exception e) {
            log.error("❌ Error handling user join in room {}: {}", roomName, e.getMessage(), e);
        }
    }

    // ==================== USER LEAVE ====================
    @MessageMapping("/chat/{roomName}/removeUser")
    public void handleUserLeave(
            @DestinationVariable String roomName,
            @Payload ChatMessage message,
            @Header(value = "simpSessionAttributes", required = false) Map<String, Object> sessionAttributes) {

        try {
            // Use sender from message if available, otherwise extract from session
            String username = null;

            if (message.getSender() != null && !message.getSender().isEmpty()) {
                username = message.getSender();
                log.info("✅ Got username from message sender: {}", username);
            } else {
                username = extractUsername(sessionAttributes);
                if (username != null) {
                    log.info("✅ Got username from session: {}", username);
                }
            }

            if (username == null || username.isEmpty()) {
                log.error("❌ Username not found for user leave");
                return;
            }

            message.setSender(username);
            message.setType("LEAVE");

            log.info("👋 User {} left room: {}", username, roomName);

            // Broadcast leave message
            messagingTemplate.convertAndSend("/topic/chat/" + roomName, message);

            // Broadcast updated participant list
            broadcastParticipants(roomName);

        } catch (Exception e) {
            log.error("❌ Error handling user leave in room {}: {}", roomName, e.getMessage(), e);
        }
    }

    // ==================== BROADCAST PARTICIPANTS ====================
    @Transactional(readOnly = true)
    protected void broadcastParticipants(String roomName) {
        try {
            Room room = roomRepo.findRoomByRoomName(roomName).orElse(null);

            if (room != null && room.getParticipant() != null && !room.getParticipant().isEmpty()) {
                log.info("📡 Broadcasting {} participants for room: {}", room.getParticipant().size(), roomName);

                messagingTemplate.convertAndSend(
                        "/topic/chat/" + roomName + "/participants",
                        room.getParticipant()
                );
            } else {
                log.warn("⚠️ Room not found or has no participants: {}", roomName);
            }
        } catch (Exception e) {
            log.error("❌ Error broadcasting participants for room {}: {}", roomName, e.getMessage(), e);
        }
    }

    // ==================== UTILITY METHODS ====================
    private String extractUsername(Map<String, Object> sessionAttributes) {
        if (sessionAttributes == null) {
            log.warn("⚠️ Session attributes not available");
            return null;
        }

        Object username = sessionAttributes.get("username");
        return username != null ? (String) username : null;
    }

    private void sendErrorMessage(String roomName, String errorContent) {
        try {
            PlaybackMessage errorMsg = new PlaybackMessage();
            errorMsg.setAction("ERROR");
            errorMsg.setContent(errorContent);

            messagingTemplate.convertAndSend("/topic/chat/" + roomName + "/playback", errorMsg);
        } catch (Exception e) {
            log.error("❌ Error sending error message: {}", e.getMessage());
        }
    }
}