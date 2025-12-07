package com.music.musicwebapplication.service;

import com.music.musicwebapplication.chatDto.PlaybackState;
import com.music.musicwebapplication.dto.FavoriteSongDto;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.redis.core.RedisTemplate;
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

    // 24 hours TTL
    private static final long TTL_24H = 24;

    // ---------------------------------------------------------
    // PLAYBACK STATE
    // ---------------------------------------------------------

    public void savePlaybackState(String roomName, PlaybackState state) {
        String key = String.format(PLAYBACK_KEY, roomName);
        redisTemplate.opsForValue().set(key, state, TTL_24H, TimeUnit.HOURS);
        log.info("💾 Playback state saved for {}: {}", roomName, state);
    }

    public Optional<PlaybackState> getPlaybackState(String roomName) {
        String key = String.format(PLAYBACK_KEY, roomName);
        Object data = redisTemplate.opsForValue().get(key);

        if (data instanceof PlaybackState state) {
            return Optional.of(state);
        }
        return Optional.empty();
    }

    public void clearPlaybackState(String roomName) {
        redisTemplate.delete(String.format(PLAYBACK_KEY, roomName));
        log.info("🧹 Cleared playback state for {}", roomName);
    }

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
