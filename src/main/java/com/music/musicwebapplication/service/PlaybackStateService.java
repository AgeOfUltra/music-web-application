package com.music.musicwebapplication.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.music.musicwebapplication.chatDto.PlaybackState;
import com.music.musicwebapplication.dto.FavoriteSongDto;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlaybackStateService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final StringRedisTemplate stringRedisTemplate;
    private final ApplicationContext applicationContext;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String PLAYBACK_KEY = "room:%s:playback";
    private static final String FAVORITES_KEY = "room:%s:favorites";
    private static final long TTL_24H = 24;

    private volatile boolean shuttingDown = false;

    // ---------------------------------------------------------
    // LIFECYCLE MANAGEMENT
    // ---------------------------------------------------------

    @PreDestroy
    public void onShutdown() {
        log.info("🛑 PlaybackStateService shutting down...");
        shuttingDown = true;

        if (!canAccessRedis()) {
            log.warn("⚠️ Redis connection not available, skipping cleanup");
            return;
        }

        try {
            cleanupPlaybackStates();
            cleanupFavorites();
            log.info("✅ PlaybackStateService shutdown cleanup completed");
        } catch (IllegalStateException e) {
            log.info("ℹ️ Redis connection already closed, cleanup skipped");
        } catch (Exception e) {
            log.error("❌ Error during shutdown cleanup: {}", e.getMessage(), e);
        }
    }

    // ---------------------------------------------------------
    // PLAYBACK STATE OPERATIONS
    // ---------------------------------------------------------

    public void savePlaybackState(String roomName, PlaybackState state) {
        if (!canAccessRedis()) {
            log.debug("⚠️ Cannot save playback state for {} - Redis unavailable", roomName);
            return;
        }

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
        if (!canAccessRedis()) {
            return Optional.empty();
        }

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
            log.warn("⚠️ Redis connection unavailable for {}: {}", roomName, e.getMessage());
            return Optional.empty();
        } catch (Exception e) {
            log.error("❌ Error retrieving playback state for {}: {}", roomName, e.getMessage(), e);
            return Optional.empty();
        }
    }

    public void clearPlaybackState(String roomName) {
        if (!canAccessRedis()) {
            log.debug("⚠️ Skipping playback state cleanup for {} - Redis unavailable", roomName);
            return;
        }

        String key = String.format(PLAYBACK_KEY, roomName);

        try {
            Boolean deleted = stringRedisTemplate.delete(key);
            if (Boolean.TRUE.equals(deleted)) {
                log.info("🗑️ Playback state deleted for: {}", roomName);
            } else {
                log.debug("⚠️ No playback state to delete for: {}", roomName);
            }
        } catch (IllegalStateException e) {
            log.warn("⚠️ Redis connection unavailable during cleanup: {}", e.getMessage());
        } catch (Exception e) {
            log.error("❌ Error deleting playback state: {}", e.getMessage(), e);
        }
    }

    // ---------------------------------------------------------
    // FAVORITES OPERATIONS
    // ---------------------------------------------------------

    public void saveFavorites(String roomName, List<FavoriteSongDto> favorites) {
        if (!canAccessRedis()) {
            log.debug("⚠️ Cannot save favorites for {} - Redis unavailable", roomName);
            return;
        }

        String key = String.format(FAVORITES_KEY, roomName);

        try {
            redisTemplate.opsForValue().set(key, favorites, TTL_24H, TimeUnit.HOURS);
            log.info("💾 Saved {} favorites for {}", favorites.size(), roomName);
        } catch (IllegalStateException e) {
            log.warn("⚠️ Redis connection unavailable for {}: {}", roomName, e.getMessage());
        } catch (Exception e) {
            log.error("❌ Error saving favorites for {}: {}", roomName, e.getMessage(), e);
        }
    }

    public List<FavoriteSongDto> getFavorites(String roomName) {
        if (!canAccessRedis()) {
            return new ArrayList<>();
        }

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
            log.warn("⚠️ Redis connection unavailable for {}: {}", roomName, e.getMessage());
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
        if (!canAccessRedis()) {
            log.debug("⚠️ Skipping favorites cleanup for {} - Redis unavailable", roomName);
            return;
        }

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

    // ---------------------------------------------------------
    // HELPER METHODS
    // ---------------------------------------------------------

    private boolean canAccessRedis() {
        if (shuttingDown && !isRedisStillAvailable()) {
            return false;
        }

        try {
            RedisConnectionFactory connectionFactory = stringRedisTemplate.getConnectionFactory();
            if (connectionFactory == null) {
                return false;
            }

            if (connectionFactory instanceof LettuceConnectionFactory) {
                return ((LettuceConnectionFactory) connectionFactory).isRunning();
            }

            return true;
        } catch (Exception e) {
            log.debug("⚠️ Cannot access Redis: {}", e.getMessage());
            return false;
        }
    }

    private boolean isRedisStillAvailable() {
        try {
            RedisConnectionFactory connectionFactory = stringRedisTemplate.getConnectionFactory();
            return connectionFactory instanceof LettuceConnectionFactory
                    && ((LettuceConnectionFactory) connectionFactory).isRunning();
        } catch (Exception e) {
            return false;
        }
    }

    private void cleanupPlaybackStates() {
        Set<String> playbackKeys = stringRedisTemplate.keys("room:*:playback");
        if (playbackKeys != null && !playbackKeys.isEmpty()) {
            Long deletedPlayback = stringRedisTemplate.delete(playbackKeys);
            log.info("🧹 Cleared {} playback state(s) during shutdown", deletedPlayback);
        }
    }

    private void cleanupFavorites() {
        Set<String> favoritesKeys = redisTemplate.keys("room:*:favorites");
        if (favoritesKeys != null && !favoritesKeys.isEmpty()) {
            Long deletedFavorites = redisTemplate.delete(favoritesKeys);
            log.info("🧹 Cleared {} favorite(s) during shutdown", deletedFavorites);
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