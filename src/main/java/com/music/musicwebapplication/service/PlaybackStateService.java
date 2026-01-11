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
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String PLAYBACK_KEY = "room:%s:playback";
    private static final String FAVORITES_KEY = "room:%s:favorites";
    private static final long TTL_24H = 24;


    // ---------------------------------------------------------
    // PLAYBACK STATE OPERATIONS
    // ---------------------------------------------------------

    public void savePlaybackState(String roomName, PlaybackState state) {


        String key = String.format(PLAYBACK_KEY, roomName);

        try {
            String jsonString = objectMapper.writeValueAsString(state);
            stringRedisTemplate.opsForValue().set(key, jsonString, TTL_24H, TimeUnit.HOURS);
            log.info("💾 Playback state saved for {}: {}", roomName, state);

            // Verify save
            if (stringRedisTemplate.opsForValue().get(key) == null) {
                log.error("❌ Failed to verify playback state save for {}", roomName);
            }
        } catch (IllegalStateException e) {
            log.warn("⚠️ Redis connection unavailable for {}: {}", roomName, e.getMessage());
        } catch (Exception e) {
            log.error("❌ Error saving playback state for {}: {}", roomName, e.getMessage(), e);
        }
    }

    public Optional<PlaybackState> getPlaybackState(String roomName) {


        String key = String.format(PLAYBACK_KEY, roomName);

        try {
            String jsonString = stringRedisTemplate.opsForValue().get(key);

            if (jsonString == null || jsonString.isEmpty()) {
                log.debug("⚠️ No playback state found for room: {}", roomName);
                return Optional.empty();
            }

            PlaybackState state = objectMapper.readValue(jsonString, PlaybackState.class);
            log.info("✅ Retrieved playback state for {}: {}", roomName, state);
            return Optional.of(state);

        } catch (IllegalStateException e) {
            log.warn("⚠️ Redis connection unavailable for  {}: {}", roomName, e.getMessage());
            return Optional.empty();
        } catch (Exception e) {
            log.error("❌ Error retrieving playback state for {}: {}", roomName, e.getMessage(), e);
            return Optional.empty();
        }
    }

    public void clearPlaybackState(String roomName) {

        String key = String.format(PLAYBACK_KEY, roomName);

        try {
            Boolean deleted = stringRedisTemplate.delete(key);
            if (Boolean.TRUE.equals(deleted)) {
                log.info("🗑️ Playback state deleted for: {}", roomName);
            } else {
                log.debug("⚠️ No playback state to delete for: {}", roomName);
            }
        } catch (IllegalStateException e) {
            log.warn("⚠️ Redis connection  unavailable during cleanup: {}", e.getMessage());
        } catch (Exception e) {
            log.error("❌ Error deleting playback state: {}", e.getMessage(), e);
        }
    }

    // ---------------------------------------------------------
    // FAVORITES OPERATIONS
    // ---------------------------------------------------------

    public void saveFavorites(String roomName, List<FavoriteSongDto> favorites) {

        String key = String.format(FAVORITES_KEY, roomName);

        try {
            redisTemplate.opsForValue().set(key, favorites, TTL_24H, TimeUnit.HOURS);
            log.info("💾 Saved {} favorites for {}", favorites.size(), roomName);
        } catch (IllegalStateException e) {
            log.warn("⚠️ Redis connection  unavailable for {}: {}", roomName, e.getMessage());
        } catch (Exception e) {
            log.error("❌ Error saving favorites for {}: {}", roomName, e.getMessage(), e);
        }
    }

    public List<FavoriteSongDto> getFavorites(String roomName) {

        String key = String.format(FAVORITES_KEY, roomName);

        try {
            Object data = redisTemplate.opsForValue().get(key);
            if (data == null) {
                return new ArrayList<>();
            }

            @SuppressWarnings("unchecked")
            List<FavoriteSongDto> favorites = (List<FavoriteSongDto>) data;
            return favorites;

        } catch (IllegalStateException e) {
            log.warn("⚠️ Redis  connection unavailable for {}: {}", roomName, e.getMessage());
            return new ArrayList<>();
        } catch (ClassCastException e) {
            log.error("❌ Favorites casting failed — clearing corrupted data ({})", roomName);
            clearCorruptedFavorites(key);
            return new ArrayList<>();
        } catch (Exception e) {
            log.error("❌ Error retrieving favorites for {}: {}", roomName, e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    public void clearFavorites(String roomName) {

        String key = String.format(FAVORITES_KEY, roomName);

        try {
            Boolean deleted = redisTemplate.delete(key);
            if (Boolean.TRUE.equals(deleted)) {
                log.info("🧹 Cleared all favorites for {}", roomName);
            } else {
                log.debug("⚠️ No favorites to clear for {}", roomName);
            }
        } catch (IllegalStateException e) {
            log.warn("⚠️ Redis connection unavailable during cleanup: {}", e.getMessage());
        } catch (Exception e) {
            log.error("❌ Error clearing favorites for {}: {}", roomName, e.getMessage(), e);
        }
    }

    private void clearCorruptedFavorites(String key) {
        try {
            redisTemplate.delete(key);
        } catch (Exception ignored) {
            // Ignore if we can't delete during shutdown or other errors
        }
    }
}