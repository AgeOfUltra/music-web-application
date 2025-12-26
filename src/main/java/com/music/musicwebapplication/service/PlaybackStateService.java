package com.music.musicwebapplication.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.music.musicwebapplication.chatDto.PlaybackState;
import com.music.musicwebapplication.dto.FavoriteSongDto;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlaybackStateService {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String PLAYBACK_KEY = "room:%s:playback";
    private static final String FAVORITES_KEY = "room:%s:favorites";

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // 24 hours TTL
    private static final long TTL_24H = 24;

    // ---------------------------------------------------------
    // PLAYBACK STATE
    // ---------------------------------------------------------

    public void savePlaybackState(String roomName, PlaybackState state) {
        String key = String.format(PLAYBACK_KEY, roomName);

        try {
            String jsonString = objectMapper.writeValueAsString(state);
            stringRedisTemplate.opsForValue().set(key, jsonString, TTL_24H, TimeUnit.HOURS);

            log.info("💾 Playback state saved for {}: {}", roomName, state);

            // Verify save
            String saved = stringRedisTemplate.opsForValue().get(key);
            if (saved == null) {
                log.error("❌ Failed to verify playback state save for {}", roomName);
            } else {
                log.info("✅ Verified: Data exists in Redis for {}", roomName);
            }
        } catch (Exception e) {
            log.error("❌ Error saving playback state for {}: {}", roomName, e.getMessage(), e);
        }
    }

    public Optional<PlaybackState> getPlaybackState(String roomName) {
        String key = String.format(PLAYBACK_KEY, roomName);

        try {
            String jsonString = stringRedisTemplate.opsForValue().get(key);

            if (jsonString == null || jsonString.isEmpty()) {
                log.warn("⚠️ No playback state found for room: {}", roomName);
                return Optional.empty();
            }

            PlaybackState state = objectMapper.readValue(jsonString, PlaybackState.class);

            log.info("✅ Retrieved playback state for {}: {}", roomName, state);
            return Optional.of(state);

        } catch (Exception e) {
            log.error("❌ Error retrieving playback state for {}: {}", roomName, e.getMessage(), e);
            return Optional.empty();
        }
    }

    public void clearPlaybackState(String roomName) {
        String key = String.format(PLAYBACK_KEY, roomName);

        try {
            Boolean deleted = stringRedisTemplate.delete(key);
            if (deleted) {
                log.info("🗑️ Playback state deleted for: {}", roomName);
            } else {
                log.warn("⚠️ No playback state to delete for: {}", roomName);
            }
        } catch (Exception e) {
            log.error("❌ Error deleting playback state: {}", e.getMessage(), e);
        }
    }

//    public void clearPlaybackState(String roomName) {
//        redisTemplate.delete(String.format(PLAYBACK_KEY, roomName));
//        log.info("🧹 Cleared playback state for {}", roomName);
//    }

    // ---------------------------------------------------------
    // FAVORITES (LIST OF FavoriteSongDto)
    // ---------------------------------------------------------

    public void saveFavorites(String roomName, List<FavoriteSongDto> favorites) {
        String key = String.format(FAVORITES_KEY, roomName);
        redisTemplate.opsForValue().set(key, favorites, TTL_24H, TimeUnit.HOURS);

        log.info("💾 Saved {} favorites for {}",
                favorites == null ? 0 : favorites.size(),
                roomName);
    }

    public List<FavoriteSongDto> getFavorites(String roomName) {
        String key = String.format(FAVORITES_KEY, roomName);
        Object data = redisTemplate.opsForValue().get(key);

        if (data == null) return new ArrayList<>();

        try {
            return (List<FavoriteSongDto>) data;
        } catch (Exception e) {
            log.error("❌ Favorites casting failed — clearing corrupted data ({})", roomName);
            redisTemplate.delete(key);
            return new ArrayList<>();
        }
    }

    public void clearFavorites(String roomName) {
        redisTemplate.delete(String.format(FAVORITES_KEY, roomName));
        log.info("🧹 Cleared all favorites for {}", roomName);
    }
}
