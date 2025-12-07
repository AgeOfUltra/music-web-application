package com.music.musicwebapplication.controller;

import com.music.musicwebapplication.chatDto.PlaybackState;
import com.music.musicwebapplication.dto.FavoriteSongDto;
import com.music.musicwebapplication.dto.SyncRequest;
import com.music.musicwebapplication.entity.Participant;
import com.music.musicwebapplication.service.PlaybackStateService;
import com.music.musicwebapplication.service.RoomService;

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

        roomService.exitFromRoom(roomName, username);

        messagingTemplate.convertAndSend("/topic/chat/" + roomName, chatMessage);

        List<Participant> participants = roomService.getRoomDetails(roomName).getParticipant();
        messagingTemplate.convertAndSend("/topic/chat/" + roomName + "/participants", participants);
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

        log.info("🎵 Playback Event: {}", msg);

        PlaybackState state = PlaybackState.fromMap(msg);

        // ✅ Ensure serverTime is set for accurate sync
        state.setServerTime(System.currentTimeMillis());

        playbackStateService.savePlaybackState(roomName, state);

        messagingTemplate.convertAndSend(
                "/topic/chat/" + roomName + "/playback",
                msg
        );
    }

    // -----------------------------------
    // PLAYBACK SYNC REQUEST
    // -----------------------------------
    @MessageMapping("/chat/{roomName}/playback/sync")
    public void syncPlayback(@DestinationVariable String roomName,
                             @Payload SyncRequest request) {

        log.info("🔄 Sync Request → room={}, user={}", roomName, request.getUsername());

        Optional<PlaybackState> optionalState = playbackStateService.getPlaybackState(roomName);

        Map<String, Object> syncResponse =
                buildPlaybackSyncMessage(optionalState.orElse(null));

        messagingTemplate.convertAndSend(
                "/topic/chat/" + roomName + "/playback/state",
                syncResponse
        );
    }

    private Map<String, Object> buildPlaybackSyncMessage(PlaybackState state) {
        Map<String, Object> msg = new HashMap<>();

        if (state == null || !state.isValid()) {
            msg.put("isPlaying", false);
            msg.put("isPaused", true);
            msg.put("valid", false);  // ✅ Add explicit valid flag
            return msg;
        }

        msg.put("songFileName", state.getSongFileName());
        msg.put("songName", state.getSongName());
        msg.put("hero", state.getHero());
        msg.put("heroine", state.getHeroine());
        msg.put("language", state.getLanguage());
        msg.put("movie", state.getMovie());
        msg.put("singer", state.getSinger());
        msg.put("isPlaying", state.isPlaying());
        msg.put("isPaused", state.isPaused());
        msg.put("timestamp", state.getTimestamp());
        msg.put("serverTime", state.getServerTime());  // ✅ Use saved serverTime
        msg.put("valid", state.isValid());  // ✅ Add explicit valid flag

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
}
