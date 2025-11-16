package com.music.musicwebapplication.controller;

import com.music.musicwebapplication.dto.ChatMessage;
import com.music.musicwebapplication.dto.FavoritesMessage;
import com.music.musicwebapplication.dto.PlaybackMessage;
import com.music.musicwebapplication.dto.SyncRequest;
import com.music.musicwebapplication.entity.Room;
import com.music.musicwebapplication.repo.RoomRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Controller;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Controller
@Slf4j
@RequiredArgsConstructor
public class ChatController {

    private final SimpMessagingTemplate messagingTemplate;
    private final RoomRepo roomRepo;

    // ==================== IN-MEMORY FAVORITES STORAGE ====================
    private final Map<String, FavoritesMessage> roomFavoritesCache = new ConcurrentHashMap<>();

    // ==================== CHAT MESSAGING ====================
    @MessageMapping("/chat/{roomName}/send")
    public void handleChatMessage(
            @DestinationVariable String roomName,
            @Payload ChatMessage message,
            @Header(value = "simpSessionAttributes", required = false) Map<String, Object> sessionAttributes) {

        try {
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

            String username = null;

            if (playbackMessage != null && playbackMessage.getSender() != null && !playbackMessage.getSender().isEmpty()) {
                username = playbackMessage.getSender();
                log.info("✅ Got username from message sender: {}", username);
            } else if (playbackMessage != null && playbackMessage.getController() != null && !playbackMessage.getController().isEmpty()) {
                username = playbackMessage.getController();
                log.info("✅ Got username from message controller: {}", username);
            } else if (sessionAttributes != null) {
                username = extractUsername(sessionAttributes);
                log.info("✅ Got username from session: {}", username);
            }

            if (username == null || username.isEmpty()) {
                log.error("❌ Username not found - Message: {}, Session: {}", playbackMessage, sessionAttributes);
                sendErrorMessage(roomName, "Username not found in session");
                return;
            }

            if (playbackMessage.getAction() == null || playbackMessage.getAction().isEmpty()) {
                log.warn("⚠️ Missing action in playback message for room: {}", roomName);
                return;
            }

            playbackMessage.setController(username);
            playbackMessage.setSender(username);

            log.info("🎵 Playback action: {} by: {} in room: {}", playbackMessage.getAction(), username, roomName);

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

            messagingTemplate.convertAndSend("/topic/chat/" + roomName, message);
            broadcastParticipants(roomName);

        } catch (Exception e) {
            log.error("❌ Error handling user join in room {}: {}", roomName, e.getMessage(), e);
        }
    }

    // ==================== USER LEAVE WITH ASYNC CLEANUP ====================
    @MessageMapping("/chat/{roomName}/removeUser")
    public void handleUserLeave(
            @DestinationVariable String roomName,
            @Payload ChatMessage message,
            @Header(value = "simpSessionAttributes", required = false) Map<String, Object> sessionAttributes) {

        try {
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

            // Broadcast LEAVE message
            messagingTemplate.convertAndSend("/topic/chat/" + roomName, message);

            // Broadcast updated participant list
            broadcastParticipants(roomName);

            // Async cleanup check - non-blocking
            asyncCleanupCheck(roomName);

        } catch (Exception e) {
            log.error("❌ Error handling user leave in room {}: {}", roomName, e.getMessage(), e);
        }
    }

    // ==================== ASYNC CLEANUP METHOD ====================
    /**
     * Asynchronously checks if room is empty and cleans up if needed.
     * This runs in a separate thread to avoid blocking the main WebSocket handler.
     *
     * @param roomName The name of the room to check
     */
    @Async("asyncTaskExecutor")
    public void asyncCleanupCheck(String roomName) {
        try {
            log.debug("🔄 [ASYNC] Starting cleanup check for room: {}", roomName);

            // Wait a bit to ensure database transaction is complete
            // Longer delay than sync version since this is non-blocking
            Thread.sleep(200);

            Room room = roomRepo.findRoomWithParticipantsByRoomName(roomName).orElse(null);

            if (room == null) {
                log.warn("⚠️ [ASYNC] Room not found during cleanup check: {}", roomName);
                return;
            }

            // Check if room is empty
            if (room.getParticipant() == null || room.getParticipant().isEmpty()) {
                log.info("🧹 [ASYNC] Room {} is empty, cleaning up favorites", roomName);

                // Cleanup favorites cache
                cleanupRoomFavorites(roomName);

                // Broadcast empty favorites to any straggler connections
                broadcastEmptyFavorites(roomName);

                log.info("✅ [ASYNC] Cleanup completed for room: {}", roomName);
            } else {
                log.debug("📊 [ASYNC] Room {} still has {} participants, skipping cleanup",
                        roomName, room.getParticipant().size());
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("⚠️ [ASYNC] Cleanup check interrupted for room: {}", roomName);
        } catch (Exception e) {
            log.error("❌ [ASYNC] Error in cleanup check for room {}: {}", roomName, e.getMessage(), e);
        }
    }

    // ==================== FAVORITES MANAGEMENT ====================
    @MessageMapping("/chat/{roomName}/favorites")
    public void handleFavorites(
            @DestinationVariable String roomName,
            @Payload FavoritesMessage message) {

        try {
            log.info("⭐ Received favorites update for room: {} - Action: {} by: {}",
                    roomName, message.getAction(), message.getUsername());

            // Store the current state in cache
            roomFavoritesCache.put(roomName, message);

            // Broadcast to all participants in the room
            messagingTemplate.convertAndSend("/topic/chat/" + roomName + "/favorites", message);

            log.info("📤 Broadcasted favorites update to room: {}", roomName);

        } catch (Exception e) {
            log.error("❌ Error handling favorites for room {}: {}", roomName, e.getMessage(), e);
        }
    }

    @MessageMapping("/chat/{roomName}/favorites/sync")
    public void syncFavorites(
            @DestinationVariable String roomName,
            @Payload SyncRequest request) {

        try {
            log.info("🔄 Sync request from user: {} for room: {}", request.getUsername(), roomName);

            // Get cached favorites for this room, or return empty list
            FavoritesMessage cachedFavorites = roomFavoritesCache.get(roomName);

            FavoritesMessage syncMessage;
            if (cachedFavorites != null && cachedFavorites.getFavorites() != null) {
                // Send current favorites state to the requester
                syncMessage = new FavoritesMessage(
                        "SYNC",
                        cachedFavorites.getFavorites(),
                        null,
                        "system",
                        System.currentTimeMillis()
                );
                log.info("✅ Sending {} cached favorites to user: {}",
                        cachedFavorites.getFavorites().size(), request.getUsername());
            } else {
                // No favorites yet, send empty list
                syncMessage = new FavoritesMessage(
                        "SYNC",
                        new ArrayList<>(),
                        null,
                        "system",
                        System.currentTimeMillis()
                );
                log.info("📭 No favorites cached for room: {}, sending empty list", roomName);
            }

            // Broadcast to all (or use convertAndSendToUser if you want to target specific user)
            messagingTemplate.convertAndSend("/topic/chat/" + roomName + "/favorites", syncMessage);

        } catch (Exception e) {
            log.error("❌ Error syncing favorites for room {}: {}", roomName, e.getMessage(), e);
        }
    }

    // ==================== BROADCAST PARTICIPANTS ====================
    protected void broadcastParticipants(String roomName) {
        try {
            Room room = roomRepo.findRoomWithParticipantsByRoomName(roomName).orElse(null);

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

    // ==================== CLEANUP METHODS ====================

    /**
     * Cleans up room favorites from cache
     */
    public void cleanupRoomFavorites(String roomName) {
        try {
            FavoritesMessage removed = roomFavoritesCache.remove(roomName);

            if (removed != null && removed.getFavorites() != null) {
                log.info("🧹 Cleaned up {} favorites for room: {}",
                        removed.getFavorites().size(), roomName);
            } else {
                log.info("🧹 No favorites to clean up for room: {}", roomName);
            }

        } catch (Exception e) {
            log.error("❌ Error cleaning up favorites for room {}: {}", roomName, e.getMessage(), e);
        }
    }

    /**
     * Broadcasts empty favorites message to notify all clients
     */
    private void broadcastEmptyFavorites(String roomName) {
        try {
            FavoritesMessage emptyMessage = new FavoritesMessage(
                    "CLEAR",
                    new ArrayList<>(),
                    null,
                    "system",
                    System.currentTimeMillis()
            );

            messagingTemplate.convertAndSend(
                    "/topic/chat/" + roomName + "/favorites",
                    emptyMessage
            );

            log.info("📤 Broadcasted empty favorites for room: {}", roomName);

        } catch (Exception e) {
            log.error("❌ Error broadcasting empty favorites for room {}: {}", roomName, e.getMessage(), e);
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

    // ==================== PUBLIC API FOR MANUAL CLEANUP ====================

    /**
     * Public method to manually trigger cleanup for a specific room.
     * Can be called from other services or scheduled tasks.
     *
     * @param roomName The name of the room to cleanup
     */
    public void manualCleanupRoom(String roomName) {
        log.info("🔧 Manual cleanup triggered for room: {}", roomName);
        asyncCleanupCheck(roomName);
    }

    /**
     * Get current cache statistics for monitoring
     */
    public Map<String, Object> getCacheStatistics() {
        Map<String, Object> stats = new ConcurrentHashMap<>();
        stats.put("totalRooms", roomFavoritesCache.size());
        stats.put("roomNames", new ArrayList<>(roomFavoritesCache.keySet()));

        int totalFavorites = roomFavoritesCache.values().stream()
                .filter(msg -> msg.getFavorites() != null)
                .mapToInt(msg -> msg.getFavorites().size())
                .sum();

        stats.put("totalFavorites", totalFavorites);

        log.debug("📊 Cache stats: {}", stats);
        return stats;
    }
}