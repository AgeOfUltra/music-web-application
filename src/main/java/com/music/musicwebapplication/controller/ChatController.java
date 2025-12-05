package com.music.musicwebapplication.controller;

import com.music.musicwebapplication.dto.ChatMessage;
import com.music.musicwebapplication.chatDto.PlaybackState;
import com.music.musicwebapplication.chatDto.PlaybackSyncRequest;
import com.music.musicwebapplication.entity.Participant;
import com.music.musicwebapplication.entity.Room;
import com.music.musicwebapplication.service.RoomService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Controller
public class ChatController {

    private final SimpMessagingTemplate messagingTemplate;
    private final RoomService roomService;

    // Store current playback state for each room
    // Key: roomName, Value: PlaybackState
    private final Map<String, PlaybackState> roomPlaybackStates = new ConcurrentHashMap<>();

    public ChatController(SimpMessagingTemplate messagingTemplate, RoomService roomService) {
        this.messagingTemplate = messagingTemplate;
        this.roomService = roomService;
    }

    // ==================== CHAT MESSAGES ====================

    @MessageMapping("/chat/{roomName}/send")
    public void sendMessage(@DestinationVariable String roomName,
                            @Payload ChatMessage chatMessage) {

        log.info("💬 Chat message from {} in room {}: {}",
                chatMessage.getSender(), roomName, chatMessage.getContent());

        chatMessage.setType(String.valueOf(ChatMessage.MessageType.CHAT));

        messagingTemplate.convertAndSend(
                "/topic/chat/" + roomName,
                chatMessage
        );
    }

    // ==================== USER JOIN/LEAVE ====================

    @MessageMapping("/chat/{roomName}/addUser")
    public void addUser(@DestinationVariable String roomName,
                        @Payload ChatMessage chatMessage,
                        SimpMessageHeaderAccessor headerAccessor) {

        String username = chatMessage.getSender();
        log.info("✅ User {} joining room: {}", username, roomName);

        // Store username in WebSocket session
        headerAccessor.getSessionAttributes().put("username", username);
        headerAccessor.getSessionAttributes().put("roomName", roomName);

        chatMessage.setType(String.valueOf(ChatMessage.MessageType.JOIN));

        // Broadcast JOIN message to all participants
        messagingTemplate.convertAndSend(
                "/topic/chat/" + roomName,
                chatMessage
        );

        // Send updated participant list
        broadcastParticipantList(roomName);

        log.info("📢 Broadcasted JOIN message for {} in room {}", username, roomName);
    }

    @MessageMapping("/chat/{roomName}/removeUser")
    public void removeUser(@DestinationVariable String roomName,
                           @Payload ChatMessage chatMessage,
                           SimpMessageHeaderAccessor headerAccessor) {

        String username = chatMessage.getSender();
        log.info("❌ User {} leaving room: {}", username, roomName);

        chatMessage.setType(String.valueOf(ChatMessage.MessageType.LEAVE));

        // Broadcast LEAVE message
        messagingTemplate.convertAndSend(
                "/topic/chat/" + roomName,
                chatMessage
        );

        // Check if the leaving user is the organizer
        try {
            Room room = roomService.getRoomDetails(roomName);
            boolean wasOrganizer = room.getParticipant().stream()
                    .anyMatch(p -> p.getUserName().equals(username) && p.isOrganizer());

            if (wasOrganizer) {
                // Clear playback state when organizer leaves
                clearPlaybackState(roomName);
                log.info("🎵 Organizer left - cleared playback state for room: {}", roomName);
            }
        } catch (Exception e) {
            log.error("Error checking organizer status on leave: {}", e.getMessage());
        }

        // Remove user from room
        try {
            roomService.exitFromRoom(roomName, username);
        } catch (Exception e) {
            log.error("Error removing user from room: {}", e.getMessage());
        }

        // Send updated participant list
        broadcastParticipantList(roomName);

        log.info("📢 Broadcasted LEAVE message for {} in room {}", username, roomName);
    }

    // ==================== PLAYBACK CONTROL (ENHANCED WITH STATE TRACKING) ====================

    @MessageMapping("/chat/{roomName}/playback")
    public void handlePlayback(@DestinationVariable String roomName,
                               @Payload Map<String, Object> playbackMessage) {

        String action = (String) playbackMessage.get("action");
        String controller = (String) playbackMessage.get("controller");

        log.info("🎵 Playback command received - Room: {}, Action: {}, Controller: {}",
                roomName, action, controller);

        // Update room state based on action
        switch (action) {
            case "PLAY":
                updatePlaybackState(roomName, playbackMessage, false, true);
                log.info("▶️ PLAY command - Updated state for room: {}", roomName);
                break;

            case "PAUSE":
                updatePlaybackState(roomName, playbackMessage, true, true);
                log.info("⏸️ PAUSE command - Updated state for room: {}", roomName);
                break;

            case "RESUME":
                updatePlaybackState(roomName, playbackMessage, false, true);
                log.info("▶️ RESUME command - Updated state for room: {}", roomName);
                break;

            default:
                log.warn("⚠️ Unknown playback action: {}", action);
        }

        // Broadcast playback command to all participants
        messagingTemplate.convertAndSend(
                "/topic/chat/" + roomName + "/playback",
                playbackMessage
        );

        log.info("📤 Playback command broadcasted to room: {}", roomName);
    }

    // ==================== NEW: PLAYBACK STATE SYNC ====================

    @MessageMapping("/chat/{roomName}/playback/sync")
    public void handleSyncRequest(@DestinationVariable String roomName,
                                  @Payload PlaybackSyncRequest syncRequest) {

        if (syncRequest == null || !syncRequest.isValid()) {
            log.warn("⚠️ Invalid sync request received for room: {}", roomName);
            return;
        }

        try {
            Room room = roomService.getRoomDetails(roomName);
            boolean userExists = room.getParticipant().stream()
                    .anyMatch(p -> p.getUserName().equals(syncRequest.getRequester()));

            if (!userExists) {
                log.warn("⚠️ Sync request from non-participant {}: ignored",
                        syncRequest.getRequester());
                return;
            }
        } catch (Exception e) {
            log.error("❌ Error validating sync request: {}", e.getMessage());
            return;
        }

        log.info("🔄 Sync request from {} in room: {}", syncRequest.getRequester(), roomName);

        PlaybackState currentState = roomPlaybackStates.get(roomName);

        if (currentState == null || !currentState.isPlaying()) {
            log.info("ℹ️ No active playback in room: {}", roomName);

            // Send empty state to indicate no playback
            PlaybackState emptyState = PlaybackState.builder()
                    .isPlaying(false)
                    .build();

            messagingTemplate.convertAndSend(
                    "/topic/chat/" + roomName + "/playback/state",
                    emptyState
            );
            return;
        }

        // Calculate current timestamp accounting for elapsed time
        long currentTime = System.currentTimeMillis();
        long elapsed = currentTime - currentState.getServerTime();

        // If paused, don't add elapsed time to timestamp
        long adjustedTimestamp = currentState.getTimestamp() +
                (currentState.isPaused() ? 0 : elapsed);

        // Create updated state with current timestamp
        PlaybackState syncState = PlaybackState.builder()
                .songFileName(currentState.getSongFileName())
                .songName(currentState.getSongName())
                .hero(currentState.getHero())
                .heroine(currentState.getHeroine())
                .singer(currentState.getSinger())
                .movie(currentState.getMovie())
                .language(currentState.getLanguage())
                .organizer(currentState.getOrganizer())
                .timestamp(adjustedTimestamp)
                .serverTime(currentTime)
                .isPlaying(true)
                .isPaused(currentState.isPaused())
                .build();

        // Send state to all participants in the room
        messagingTemplate.convertAndSend(
                "/topic/chat/" + roomName + "/playback/state",
                syncState
        );

        log.info("✅ Sent playback state to {}: {} at {}ms (paused: {})",
                syncRequest.getRequester(),
                syncState.getSongName(),
                adjustedTimestamp,
                syncState.isPaused());
    }

    // ==================== SKIP COMMANDS ====================

    @MessageMapping("/chat/{roomName}/skip")
    public void handleSkipCommand(@DestinationVariable String roomName,
                                  @Payload Map<String, Object> skipMessage) {

        String action = (String) skipMessage.get("action");
        String controller = (String) skipMessage.get("controller");

        log.info("⏭️ Skip command received - Room: {}, Action: {}, Controller: {}",
                roomName, action, controller);

        messagingTemplate.convertAndSend(
                "/topic/chat/" + roomName + "/skip",
                skipMessage
        );

        log.info("📤 Skip command broadcasted to room: {}", roomName);
    }

    // ==================== TYPING INDICATOR ====================

    @MessageMapping("/chat/{roomName}/typing")
    public void handleTyping(@DestinationVariable String roomName,
                             @Payload Map<String, Object> typingMessage) {

        messagingTemplate.convertAndSend(
                "/topic/chat/" + roomName + "/typing",
                typingMessage
        );
    }

    // ==================== ROOM FAVORITES ====================

    @MessageMapping("/chat/{roomName}/favorites")
    public void handleFavorites(@DestinationVariable String roomName,
                                @Payload Map<String, Object> favoritesMessage) {

        String action = (String) favoritesMessage.get("action");
        String username = (String) favoritesMessage.get("username");

        log.info("❤️ Favorites update - Room: {}, Action: {}, User: {}",
                roomName, action, username);

        messagingTemplate.convertAndSend(
                "/topic/chat/" + roomName + "/favorites",
                favoritesMessage
        );

        log.info("📤 Favorites update broadcasted to room: {}", roomName);
    }

    @MessageMapping("/chat/{roomName}/favorites/sync")
    public void handleFavoritesSync(@DestinationVariable String roomName,
                                    @Payload Map<String, Object> syncRequest) {

        log.info("🔄 Favorites sync request in room: {}", roomName);

        // Here you would typically fetch favorites from database/cache
        // For now, just broadcast a SYNC action to trigger client-side state sharing
        Map<String, Object> syncMessage = Map.of(
                "action", "SYNC",
                "username", "system",
                "favorites", List.of() // Empty list, clients will update from their state
        );

        messagingTemplate.convertAndSend(
                "/topic/chat/" + roomName + "/favorites",
                syncMessage
        );
    }

    // ==================== HELPER METHODS ====================

    /**
     * Update internal playback state for a room
     */
    private void updatePlaybackState(String roomName,
                                     Map<String, Object> playbackMessage,
                                     boolean isPaused,
                                     boolean isPlaying) {

        String songFileName = (String) playbackMessage.get("songFileName");

        if (songFileName == null || songFileName.isEmpty()) {
            log.warn("⚠️ No songFileName in playback message, skipping state update");
            return;
        }

        Object timestampObj = playbackMessage.get("timestamp");
        long timestamp = 0;

        if (timestampObj instanceof Number) {
            timestamp = ((Number) timestampObj).longValue();
        }

        PlaybackState state = PlaybackState.builder()
                .songFileName(songFileName)
                .songName((String) playbackMessage.get("songName"))
                .hero((String) playbackMessage.get("hero"))
                .heroine((String) playbackMessage.get("heroine"))
                .singer((String) playbackMessage.get("singer"))
                .movie((String) playbackMessage.get("movie"))
                .language((String) playbackMessage.get("language"))
                .organizer((String) playbackMessage.get("controller"))
                .timestamp(timestamp)
                .serverTime(System.currentTimeMillis())
                .isPlaying(isPlaying)
                .isPaused(isPaused)
                .build();

        roomPlaybackStates.put(roomName, state);

        log.info("💾 Updated playback state for room {}: {} ({}ms) - Playing: {}, Paused: {}",
                roomName,
                state.getSongName(),
                timestamp,
                isPlaying,
                isPaused);
    }

    /**
     * Clear playback state for a room (called when organizer leaves or room ends)
     */
    public void clearPlaybackState(String roomName) {
        PlaybackState removedState = roomPlaybackStates.remove(roomName);

        if (removedState != null) {
            log.info("🗑️ Cleared playback state for room {}: {}",
                    roomName, removedState.getSongName());
        } else {
            log.info("🗑️ No playback state to clear for room: {}", roomName);
        }
    }

    /**
     * Get current playback state for a room (for debugging/monitoring)
     */
    public PlaybackState getPlaybackState(String roomName) {
        return roomPlaybackStates.get(roomName);
    }

    /**
     * Broadcast updated participant list to all users in the room
     */
    private void broadcastParticipantList(String roomName) {
        try {
            Room room = roomService.getRoomDetails(roomName);
            List<Participant> participants = room.getParticipant();

            messagingTemplate.convertAndSend(
                    "/topic/chat/" + roomName + "/participants",
                    participants
            );

            log.info("📋 Broadcasted participant list for room {}: {} participants",
                    roomName, participants.size());

        } catch (Exception e) {
            log.error("❌ Error broadcasting participant list for room {}: {}",
                    roomName, e.getMessage());
        }
    }

    /**
     * Clean up room resources (call this when room is deleted)
     */
    public void cleanupRoom(String roomName) {
        clearPlaybackState(roomName);
        log.info("🧹 Cleaned up resources for room: {}", roomName);
    }
}