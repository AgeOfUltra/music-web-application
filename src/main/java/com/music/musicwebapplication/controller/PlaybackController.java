package com.music.musicwebapplication.controller;

import com.music.musicwebapplication.entity.Room;
import com.music.musicwebapplication.exception.RoomNotFoundException;
import com.music.musicwebapplication.repo.RoomRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Controller
@Slf4j
@RequiredArgsConstructor
public class PlaybackController {

    private final SimpMessagingTemplate messagingTemplate;
    private final RoomRepo roomRepo;

    @MessageMapping("/chat/{roomName}/playback")
    @Transactional(readOnly = true)
    public void handlePlayback(@DestinationVariable String roomName,
                               @Payload Map<String, Object> message,
                               @Header(value = "simpSessionAttributes", required = false) Map<String, Object> sessionAttributes) {

        try {
            // Handle null session attributes
            if (sessionAttributes == null) {
                log.error("Session attributes is null for room: {}", roomName);
                Map<String, Object> errorMessage = Map.of(
                        "action", "ERROR",
                        "content", "Session attributes not found"
                );
                messagingTemplate.convertAndSend("/topic/chat/" + roomName + "/playback", errorMessage);
                return;
            }

            String username = (String) sessionAttributes.get("username");

            if (username == null || username.isEmpty()) {
                log.error("Username not found in session attributes for room: {}. Available attributes: {}",
                        roomName, sessionAttributes.keySet());
                Map<String, Object> errorMessage = Map.of(
                        "action", "ERROR",
                        "content", "Username not found in session"
                );
                messagingTemplate.convertAndSend("/topic/chat/" + roomName + "/playback", errorMessage);
                return;
            }

            log.info("Playback request from user: {} in room: {}, action: {}",
                    username, roomName, message.get("action"));

            // Find room - case insensitive search
            Room room = roomRepo.findRoomByRoomName(roomName)
                    .orElseThrow(() -> {
                        log.error("Room not found: {}", roomName);
                        return new RoomNotFoundException("Room not found: " + roomName);
                    });

            // Check if user is organizer
            boolean isOrganizer = room.getParticipant().stream()
                    .anyMatch(p -> p.getUserName().equals(username) && p.isOrganizer());

            if (!isOrganizer) {
                log.warn("User {} attempted playback control in room {} without organizer rights",
                        username, roomName);
                // Don't throw exception - just ignore the request
                return;
            }

            // Add controller username to message
            message.put("controller", username);

            // Broadcast to all users in the room
            messagingTemplate.convertAndSend("/topic/chat/" + roomName + "/playback", message);

            log.info("Playback {} action broadcasted in room {} by {}",
                    message.get("action"), roomName, username);

        } catch (RoomNotFoundException e) {
            log.error("Room not found exception: {}", e.getMessage());
            // Send error message to client
            Map<String, Object> errorMessage = Map.of(
                    "action", "ERROR",
                    "content", "Room not found"
            );
            messagingTemplate.convertAndSend("/topic/chat/" + roomName + "/playback", errorMessage);
        } catch (Exception e) {
            log.error("Unexpected error handling playback in room {}: {}", roomName, e.getMessage(), e);
            // Send error message to client
            Map<String, Object> errorMessage = Map.of(
                    "action", "ERROR",
                    "content", "Playback error: " + e.getMessage()
            );
            try {
                messagingTemplate.convertAndSend("/topic/chat/" + roomName + "/playback", errorMessage);
            } catch (Exception sendError) {
                log.error("Failed to send error message: {}", sendError.getMessage());
            }
        }
    }
}