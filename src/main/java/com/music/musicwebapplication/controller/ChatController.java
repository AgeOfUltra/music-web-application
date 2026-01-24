package com.music.musicwebapplication.controller;

import com.music.musicwebapplication.chatDto.PlaybackState;
import com.music.musicwebapplication.dto.FavoriteSongDto;
import com.music.musicwebapplication.dto.SyncRequest;
import com.music.musicwebapplication.entity.Participant;
import com.music.musicwebapplication.entity.Room;
import com.music.musicwebapplication.entity.UserSession;
import com.music.musicwebapplication.service.PlaybackStateService;
import com.music.musicwebapplication.service.RoomService;

import com.music.musicwebapplication.service.UserSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.*;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

@Slf4j
@RestController
@RequiredArgsConstructor
public class ChatController {

    private final SimpMessagingTemplate messagingTemplate;
    private final PlaybackStateService playbackStateService;
    private final RoomService roomService;
    private final UserSessionService sessionService;

    // -----------------------------------
    // CHAT
    // -----------------------------------
    @MessageMapping("/chat/{roomName}/send")
    public void sendMessage(@DestinationVariable String roomName,
                            @Payload Map<String, Object> chatMessage) {
        messagingTemplate.convertAndSend("/topic/chat/" + roomName, chatMessage);
    }

    @MessageMapping("/chat/{roomName}/addUser")
    public void addUser(@DestinationVariable String roomName,
                        @Payload Map<String, Object> chatMessage) {
        messagingTemplate.convertAndSend("/topic/chat/" + roomName, chatMessage);
    }


    @GetMapping("/chat/session/validate")
    public ResponseEntity<Map<String, Object>> validateSession(
            @RequestHeader("Authorization") String authHeader) {

        // If we reach here, JWT filter already validated the session
        // (Otherwise it would have returned 401)

        return ResponseEntity.ok(Map.of(
                "status", "valid",
                "timestamp", System.currentTimeMillis()
        ));
    }

    @MessageMapping("/chat/{roomName}/removeUser")
    public void removeUser(@DestinationVariable String roomName,
                           @Payload Map<String, Object> chatMessage) {

        String username = (String) chatMessage.get("sender");

        log.info("🔌 WebSocket disconnect notification for user={} in room={}", username, roomName);

        // ✅ CRITICAL: Broadcast to all users in the room
        try {
            messagingTemplate.convertAndSend("/topic/chat/" + roomName, chatMessage);
            log.info("📢 Broadcasted LEAVE notification to /topic/chat/{}", roomName);
        } catch (Exception e) {
            log.error("❌ Failed to broadcast LEAVE message: {}", e.getMessage(), e);
        }

        try {
            UserSession session = sessionService.getUserSession(username);
            if (session != null && roomName.equals(session.getRoomName())) {
                log.warn("⚠️ User {} still has room reference after disconnect - HTTP may have failed", username);
            } else {
                log.info("✅ User {} already cleaned up by HTTP endpoint", username);
            }
        } catch (Exception e) {
            log.debug("Session check failed during disconnect notification: {}", e.getMessage());
        }
    }

    // -----------------------------------
    // TYPING
    // -----------------------------------
    @MessageMapping("/chat/{roomName}/typing")
    public void typing(@DestinationVariable String roomName,
                       @Payload Map<String, Object> msg) {
        messagingTemplate.convertAndSend("/topic/chat/" + roomName + "/typing", msg);
    }

    // -----------------------------------
    // PARTICIPANTS
    // -----------------------------------
    @MessageMapping("/chat/{roomName}/participants")
    public void participants(@DestinationVariable String roomName) {
        List<Participant> participants = roomService.getRoomDetails(roomName).getParticipant();
        messagingTemplate.convertAndSend("/topic/chat/" + roomName + "/participants", participants);
    }

    // -----------------------------------
    // PLAYBACK EVENT
    // -----------------------------------
    @MessageMapping("/chat/{roomName}/playback")
    public void handlePlayback(@DestinationVariable String roomName,
                               @Payload Map<String, Object> msg) {

        log.info("🎵 Playback Event → action={}, song={}",
                msg.get("action"), msg.get("songName"));

        PlaybackState state = PlaybackState.fromMap(msg);

        // Set server timestamp for accurate sync
        long serverTime = System.currentTimeMillis();
        state.setServerTime(serverTime);

        // For PLAY action, timestamp should be 0 (start from beginning)
        if ("PLAY".equals(msg.get("action"))) {
            state.setTimestamp(0L);
            state.setPlaying(true);
            state.setPaused(false);
            log.debug("▶️ PLAY action → Starting song from beginning");
        }
        // For RESUME action, use provided timestamp
        else if ("RESUME".equals(msg.get("action"))) {
            state.setPlaying(true);
            state.setPaused(false);
            log.debug("▶️ RESUME action → timestamp={}ms", state.getTimestamp());
        }
        // For PAUSE action, save current timestamp
        else if ("PAUSE".equals(msg.get("action"))) {
            state.setPlaying(false);
            state.setPaused(true);
            log.debug("⏸️ PAUSE action → timestamp={}ms", state.getTimestamp());
        }

        // Save to Redis
        playbackStateService.savePlaybackState(roomName, state);

        log.debug("💾 Saved playback state → room={}, valid={}", roomName, state.isValid());

        // Broadcast to all participants
        messagingTemplate.convertAndSend(
                "/topic/chat/" + roomName + "/playback",
                msg
        );

        log.info("📡 Broadcast playback event → room={}", roomName);
    }

    // -----------------------------------
    // PLAYBACK SYNC REQUEST
    // -----------------------------------
    @MessageMapping("/chat/{roomName}/playback/sync")
    public void syncPlayback(@DestinationVariable String roomName,
                             @Payload SyncRequest request) {

        log.info("🔄 Sync Request → room={}, user={}, timestamp={}",
                roomName, request.getUsername(), request.getTimestamp());

        // ⭐ NEW CODE: Check if room has an organizer
        try {
            Room room = roomService.getRoomDetails(roomName);

            boolean hasOrganizer = room.getParticipant().stream()
                    .anyMatch(Participant::isOrganizer);

            if (!hasOrganizer) {
                log.info("ℹ️ No organizer in room {} - returning invalid sync state", roomName);

                Map<String, Object> emptyResponse = new HashMap<>();
                emptyResponse.put("valid", false);
                emptyResponse.put("isPlaying", false);
                emptyResponse.put("isPaused", false);
                emptyResponse.put("serverTime", System.currentTimeMillis());

                messagingTemplate.convertAndSend(
                        "/topic/chat/" + roomName + "/playback/state",
                        emptyResponse
                );
                return;
            }
        } catch (Exception e) {
            log.warn("⚠️ Could not verify organizer presence: {}", e.getMessage());
        }

        // Get current playback state from Redis
        Optional<PlaybackState> optionalState = playbackStateService.getPlaybackState(roomName);

        // Build sync response with current server time
        Map<String, Object> syncResponse = buildPlaybackSyncMessage(optionalState.orElse(null));

        // Add request metadata for debugging
        syncResponse.put("requestedBy", request.getUsername());
        syncResponse.put("requestTime", request.getTimestamp());
        syncResponse.put("responseTime", System.currentTimeMillis());

        log.debug("📤 Sending sync response → valid={}, isPlaying={}, song={}",
                syncResponse.get("valid"),
                syncResponse.get("isPlaying"),
                syncResponse.get("songName"));

        // Send response to specific user or broadcast to room
        // Option 1: Send to everyone (current implementation)
        messagingTemplate.convertAndSend(
                "/topic/chat/" + roomName + "/playback/state",
                syncResponse
        );

        // Option 2: Send only to requesting user (more efficient)
        // messagingTemplate.convertAndSendToUser(
        //         request.getUsername(),
        //         "/queue/playback/state",
        //         syncResponse
        // );
    }

    private Map<String, Object> buildPlaybackSyncMessage(PlaybackState state) {
        Map<String, Object> msg = new HashMap<>();

        // Current server time for sync calculation
        long currentServerTime = System.currentTimeMillis();

        if (state == null || !state.isValid()) {
            log.info("ℹ️ No valid playback state found");
            msg.put("valid", false);
            msg.put("isPlaying", false);
            msg.put("isPaused", true);
            msg.put("serverTime", currentServerTime);
            return msg;
        }

        // Calculate current playback position
        long elapsedSinceLastUpdate = currentServerTime - state.getServerTime();
        long adjustedTimestamp = state.getTimestamp();

        // If playing (not paused), adjust timestamp for elapsed time
        if (state.isPlaying() && !state.isPaused()) {
            adjustedTimestamp += elapsedSinceLastUpdate;
            log.info("⏱️ Adjusted timestamp: original={}, elapsed={}ms, adjusted={}",
                    state.getTimestamp(), elapsedSinceLastUpdate, adjustedTimestamp);
        }

        // Build complete sync message
        msg.put("valid", true);
        msg.put("songFileName", state.getSongFileName());
        msg.put("songName", state.getSongName());
        msg.put("hero", state.getHero());
        msg.put("heroine", state.getHeroine());
        msg.put("language", state.getLanguage());
        msg.put("movie", state.getMovie());
        msg.put("singer", state.getSinger());
        msg.put("isPlaying", state.isPlaying());
        msg.put("isPaused", state.isPaused());
        msg.put("timestamp", adjustedTimestamp);  // Use adjusted timestamp
        msg.put("serverTime", currentServerTime);  // Current server time for client sync

        log.info("✅ Built sync message → song={}, timestamp={}ms, isPlaying={}",
                state.getSongName(), adjustedTimestamp, state.isPlaying());

        return msg;
    }

    // -----------------------------------
    // FAVORITES
    // -----------------------------------
    @MessageMapping("/chat/{roomName}/favorites")
    public void updateFavorites(@DestinationVariable String roomName,
                                @Payload Map<String, Object> msg) {

        List<Map<String, Object>> rawList =
                (List<Map<String, Object>>) msg.get("favorites");

        List<FavoriteSongDto> dtoList = new ArrayList<>();

        if (rawList != null) {
            for (Map<String, Object> map : rawList) {
                FavoriteSongDto dto = FavoriteSongDto.builder()
                        .fileName((String) map.get("fileName"))
                        .songName((String) map.get("songName"))
                        .hero((String) map.get("hero"))
                        .heroine((String) map.get("heroine"))
                        .singer((String) map.get("singer"))
                        .movie((String) map.get("movie"))
                        .language((String) map.get("language"))
                        .requestedBy((String) map.get("requestedBy"))
                        .requestedAt(map.get("requestedAt") == null ? null :
                                Long.valueOf(String.valueOf(map.get("requestedAt"))))
                        .build();

                dtoList.add(dto);
            }
        }

        // Save in Redis with 24h TTL
        playbackStateService.saveFavorites(roomName, dtoList);

        // Broadcast original message
        messagingTemplate.convertAndSend(
                "/topic/chat/" + roomName + "/favorites",
                msg
        );
    }

    @MessageMapping("/chat/{roomName}/favorites/sync")
    public void syncFavorites(@DestinationVariable String roomName,
                              @Payload Map<String, Object> req) {

        List<FavoriteSongDto> favorites = playbackStateService.getFavorites(roomName);

        Map<String, Object> response = new HashMap<>();
        response.put("action", "SYNC");
        response.put("favorites", favorites);
        response.put("username", req.get("username"));

        messagingTemplate.convertAndSend(
                "/topic/chat/" + roomName + "/favorites",
                response
        );
    }

    // -----------------------------------
    // SKIP
    // -----------------------------------
    @MessageMapping("/chat/{roomName}/skip")
    public void skip(@DestinationVariable String roomName,
                     @Payload Map<String, Object> msg) {

        messagingTemplate.convertAndSend(
                "/topic/chat/" + roomName + "/skip",
                msg
        );
    }

    @MessageMapping("/chat/{roomName}/heartbeat")
    public void handleHeartbeat(@Payload Map<String, Object> heartbeat,
                                @DestinationVariable String roomName) {
        // Just log or ignore - this keeps connection alive
        // No need to send response
    }
}
