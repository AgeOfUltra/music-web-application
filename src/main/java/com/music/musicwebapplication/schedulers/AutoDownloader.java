package com.music.musicwebapplication.schedulers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.music.musicwebapplication.dto.FavoriteSongDto;
import com.music.musicwebapplication.service.PlaybackStateService;
import com.music.musicwebapplication.service.RoomService;
import com.music.musicwebapplication.service.SongCacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;


@Service
@Slf4j
public class AutoDownloader {
    private final SongCacheService songService;
    private final PlaybackStateService cacheService;
    private final RoomService roomService;
    private final ObjectMapper objectMapper;


    public AutoDownloader(SongCacheService songService, PlaybackStateService cacheService, RoomService roomService, ObjectMapper objectMapper) {
        this.songService = songService;
        this.cacheService = cacheService;
        this.roomService = roomService;
        this.objectMapper = objectMapper;
    }

    @Scheduled(cron = "0 */1 * * * *") // Every 1 minute
    public void autoDownloadFavSongs() {
        log.info("Starting auto-download scheduler at: {}", LocalDateTime.now());

        List<String> availableRoomNames = roomService.getAllActiveRoomNames();

        if (availableRoomNames.isEmpty()) {
            log.info("No Active Rooms Available");
            return;
        }

        log.info("Found {} active rooms. Processing favorites...", availableRoomNames.size());

        // Process each room asynchronously
        availableRoomNames.forEach(roomName -> {
            try {
                processFavoritesForRoom(roomName);
            } catch (Exception e) {
                log.error("Error processing room {}: {}", roomName, e.getMessage());
            }
        });

        log.info("All room processing tasks triggered");
    }

    @Async("asyncTaskExecutor")
    public void processFavoritesForRoom(String roomName) {
        log.info("Processing favorites for room: {} on thread: {}",
                roomName, Thread.currentThread().getName());

        try {
            // Get favorite songs for this room
            List<?> rawFavorites = cacheService.getFavorites(roomName);

            if (rawFavorites.isEmpty()) {
                log.info("No favorite songs found for room: {}", roomName);
                CompletableFuture.completedFuture(null);
                return;
            }

            // Safely extract fileNames
            List<String> favoriteSongs = rawFavorites.stream()
                    .map(this::extractFileName)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();

            log.info("Found {} favorite songs in room: {}", favoriteSongs.size(), roomName);

            // Download each song asynchronously
            List<CompletableFuture<Boolean>> downloadFutures = favoriteSongs.stream()
                    .map(this::processSongDownload)
                    .toList();

            // Wait for all downloads to complete
            CompletableFuture<Void> allDownloads = CompletableFuture.allOf(
                    downloadFutures.toArray(new CompletableFuture[0])
            );

            allDownloads.thenRun(() -> {
                long successCount = downloadFutures.stream()
                        .filter(CompletableFuture::join)
                        .count();

                log.info("Room {}: Downloaded {}/{} songs successfully",
                        roomName, successCount, favoriteSongs.size());
            }).exceptionally(ex -> {
                log.error("Error downloading songs for room {}: {}", roomName, ex.getMessage());
                return null;
            });

        } catch (Exception e) {
            log.error("Error processing favorites for room {}: {}", roomName, e.getMessage(), e);
            CompletableFuture.failedFuture(e);
        }
    }

    private String extractFileName(Object obj) {
        try {
            if (obj instanceof Map) {
                return (String) ((Map<?, ?>) obj).get("fileName");
            } else if (obj instanceof FavoriteSongDto) {
                return ((FavoriteSongDto) obj).getFileName();
            } else {
                // Try converting with ObjectMapper
                FavoriteSongDto dto = objectMapper.convertValue(obj, FavoriteSongDto.class);
                return dto.getFileName();
            }
        } catch (Exception e) {
            log.warn("Failed to extract fileName from object: {}", e.getMessage());
            return null;
        }
    }

    public CompletableFuture<Boolean> processSongDownload(String songName) {
        log.info("Downloading song: {} on thread: {}",
                songName, Thread.currentThread().getName());

        try {
            boolean result = songService.songAutoDownload(songName);

            if (result) {
                log.info("Successfully downloaded: {}", songName);
            } else {
                log.warn("Failed to download: {}", songName);
            }

            return CompletableFuture.completedFuture(result);

        } catch (Exception e) {
            log.error("Unexpected error downloading song {}: {}", songName, e.getMessage());
            throw new RuntimeException("Download failed for: " + songName, e);
        }
    }
}
