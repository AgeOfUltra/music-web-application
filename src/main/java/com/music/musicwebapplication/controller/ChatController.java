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

import org.springframework.messaging.handler.annotation.*;
import org.springframework.messaging.simp.SimpMessagingTemplate;
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


    @MessageMapping("/chat/{roomName}/removeUser")
    public void removeUser(@DestinationVariable String roomName,
                           @Payload Map<String, Object> chatMessage) {

        String username = (String) chatMessage.get("sender");

        log.info("🔌 WebSocket DISCONNECT → user={}, room={}", username, roomName);

        try {
            // ✅ CHECK 1: Verify session still has this room
            UserSession session = sessionService.getUserSession(username);
            if (session == null) {
                log.info("ℹ️ No session found for {} - already cleaned up", username);
                return;
            }

            if (!roomName.equals(session.getRoomName())) {
                log.info("ℹ️ User {} not in room {} anymore (currently in {})",
                        username, roomName, session.getRoomName());
                return;
            }

            // ✅ CHECK 2: Verify room still exists
            Optional<Room> roomOpt = Optional.ofNullable(roomService.getRoomDetails(roomName));
            if (roomOpt.isEmpty()) {
                log.info("ℹ️ Room {} not found - already deleted by HTTP request", roomName);

                // Only clear room reference if session still points to this room
                if (roomName.equals(session.getRoomName())) {
                    sessionService.updateRoomName(username, null);
                    log.info("✅ Cleared room reference for {} (room already deleted)", username);
                }
                return;
            }

            Room room = roomOpt.get();

            // ✅ CHECK 3: Verify user still in room
            boolean userExists = room.getParticipant().stream()
                    .anyMatch(p -> p.getUserName().equals(username));

            if (!userExists) {
                log.info("ℹ️ User {} not in room {} - already removed by HTTP request", username, roomName);

                // Only clear room reference if session still points to this room
                if (roomName.equals(session.getRoomName())) {
                    sessionService.updateRoomName(username, null);
                    log.info("✅ Cleared room reference for {} (user already removed)", username);
                }
                return;
            }

            // ✅ CRITICAL: Check if this is intentional exit (back button was pressed)
            // If intentionalLogout flag is set, HTTP DELETE already handled it
            if (session.isIntentionalLogout()) {
                log.info("🔖 Intentional exit detected for {} - HTTP already handling, skipping WebSocket cleanup",
                        username);
                return;
            }

            // ✅ At this point: unintentional disconnect (tab close, network issue)
            // Use fullLogout=false to keep session alive (will expire via TTL)
            log.info("🔌 Unintentional disconnect detected for {} - clearing room only", username);

            boolean removed = roomService.exitFromRoom(roomName, username, false);

            if (!removed) {
                log.info("ℹ️ User {} already removed by concurrent operation", username);
                return;
            }

            log.info("✅ User {} removed from room {} via WebSocket (session kept)", username, roomName);

            // ✅ Broadcast updates only if room still has participants
            boolean isLastOrganizer = room.getParticipant().size() == 1 &&
                    room.getParticipant().get(0).isOrganizer();

            if (!isLastOrganizer) {
                // Send leave message
                chatMessage.put("type", "LEAVE");
                chatMessage.put("content", username + " left the room");
                chatMessage.put("timestamp", System.currentTimeMillis());
                messagingTemplate.convertAndSend("/topic/chat/" + roomName, chatMessage);

                // Update participant list
                try {
                    Optional<Room> updatedRoom = Optional.ofNullable(roomService.getRoomDetails(roomName));
                    if (updatedRoom.isPresent()) {
                        List<Participant> participants = updatedRoom.get().getParticipant();
                        messagingTemplate.convertAndSend(
                                "/topic/chat/" + roomName + "/participants",
                                participants
                        );
                        log.info("✅ Broadcasted WebSocket exit notifications for {}", username);
                    }
                } catch (Exception e) {
                    log.warn("⚠️ Could not broadcast participant update: {}", e.getMessage());
                }
            } else {
                log.info("ℹ️ Last organizer left - room {} deleted", roomName);
            }

        } catch (Exception e) {
            log.error("❌ Error in removeUser for room {} and user {}: {}",
                    roomName, username, e.getMessage());

            // ✅ On error: Only clear room reference, don't delete session
            try {
                UserSession session = sessionService.getUserSession(username);
                if (session != null && roomName.equals(session.getRoomName())) {
                    sessionService.updateRoomName(username, null);
                    log.info("✅ Emergency room reference cleanup for {}", username);
                }
            } catch (Exception sessionEx) {
                log.error("❌ Failed emergency cleanup for {}: {}", username, sessionEx.getMessage());
            }
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
            log.info("▶️ PLAY action → Starting song from beginning");
        }
        // For RESUME action, use provided timestamp
        else if ("RESUME".equals(msg.get("action"))) {
            state.setPlaying(true);
            state.setPaused(false);
            log.info("▶️ RESUME action → timestamp={}ms", state.getTimestamp());
        }
        // For PAUSE action, save current timestamp
        else if ("PAUSE".equals(msg.get("action"))) {
            state.setPlaying(false);
            state.setPaused(true);
            log.info("⏸️ PAUSE action → timestamp={}ms", state.getTimestamp());
        }

        // Save to Redis
        playbackStateService.savePlaybackState(roomName, state);

        log.info("💾 Saved playback state → room={}, valid={}", roomName, state.isValid());

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

        // Get current playback state from Redis
        Optional<PlaybackState> optionalState = playbackStateService.getPlaybackState(roomName);

        // Build sync response with current server time
        Map<String, Object> syncResponse = buildPlaybackSyncMessage(optionalState.orElse(null));

        // Add request metadata for debugging
        syncResponse.put("requestedBy", request.getUsername());
        syncResponse.put("requestTime", request.getTimestamp());
        syncResponse.put("responseTime", System.currentTimeMillis());

        log.info("📤 Sending sync response → valid={}, isPlaying={}, song={}",
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
