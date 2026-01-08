package com.music.musicwebapplication.service;

import com.music.musicwebapplication.entity.Song;
import com.music.musicwebapplication.exception.SongNotFoundException;
import com.music.musicwebapplication.repo.SongRepo;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class SongCacheService {

    private final S3Client client;
    private final SongRepo repo;

    @Value("${aws.bucket.name}")
    private String bucketName;

    @Value("${app.cache-dir:${user.dir}/song-cache}")
    private String cacheDirPath;

    private Path cacheDir;

    @PostConstruct
    public void initCacheDirectory() throws IOException {
        cacheDir = Paths.get(cacheDirPath);
        if (!Files.exists(cacheDir)) {
            Files.createDirectories(cacheDir);
        }
        log.info("📂 Song cache directory initialized at: {}", cacheDir.toAbsolutePath());
    }

    /**
     * Main entry point - gets cached resource with retry logic
     */
    public Resource getCachedResource(String objectKey) throws IOException {
        try {
            String cachedPath = cacheSongIfNeeded(objectKey);
            return new FileSystemResource(cachedPath);

        } catch (SocketException e) {
            // Client disconnected - this is normal behavior
            log.debug("⚠️ Client disconnected while fetching song: {}", objectKey);
            return null;

        } catch (SongNotFoundException e) {
            log.error("❌ Song not found in database: {}", objectKey);
            throw e;

        } catch (IOException e) {
            log.error("❌ Failed to get cached resource for {}: {}", objectKey, e.getMessage());
            throw e;
        }
    }

    /**
     * Caches song with metadata caching
     */
    @Cacheable(value = "songCacheMetadata", key = "#objectKey")
    public String cacheSongIfNeeded(String objectKey) throws IOException {
        if (!Files.exists(cacheDir)) {
            Files.createDirectories(cacheDir);
        }

        Path cachedFile = cacheDir.resolve(objectKey);

        // Check if already cached
        if (Files.exists(cachedFile)) {
            log.debug("✅ Song found in local cache: {}", objectKey);
            return cachedFile.toAbsolutePath().toString();
        }

        // Verify song exists in database
        Optional<Song> song = repo.findSongByFileName(objectKey);
        if (song.isEmpty()) {
            throw new SongNotFoundException("Song not found in DB: " + objectKey);
        }

        // Download from S3 with retry logic
        downloadFromS3WithRetry(objectKey, cachedFile);

        log.info("✅ Successfully cached {} locally", objectKey);
        return cachedFile.toAbsolutePath().toString();
    }

    /**
     * Downloads from S3 with automatic retry on network errors
     */
    @Retryable(
            retryFor = {SocketException.class, SocketTimeoutException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    private void downloadFromS3WithRetry(String objectKey, Path cachedFile) throws IOException {
        log.debug("📥 Downloading song {} from S3 (bucket: {})", objectKey, bucketName);

        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(objectKey)
                .build();

        try (ResponseInputStream<GetObjectResponse> s3Stream = client.getObject(request);
             OutputStream out = Files.newOutputStream(cachedFile)) {

            // Transfer with progress tracking
            byte[] buffer = new byte[8192];
            int bytesRead;
            long totalBytes = 0;

            while ((bytesRead = s3Stream.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
                totalBytes += bytesRead;
            }

            log.debug("📦 Downloaded {} bytes for {}", totalBytes, objectKey);

        } catch (SocketException | SocketTimeoutException e) {
            log.warn("⚠️ Network error downloading {}, will retry: {}", objectKey, e.getMessage());

            // Clean up partial file before retry
            try {
                Files.deleteIfExists(cachedFile);
                log.debug("🧹 Cleaned up partial file for {}", objectKey);
            } catch (IOException cleanupError) {
                log.warn("⚠️ Failed to cleanup partial file : {}", cleanupError.getMessage());
            }

            throw e; // Trigger retry

        } catch (IOException e) {
            log.error("❌ IO error downloading {}: {}", objectKey, e.getMessage());

            // Clean up partial file
            try {
                Files.deleteIfExists(cachedFile);
            } catch (IOException cleanupError) {
                log.warn("⚠️ Failed to cleanup partial file: {}", cleanupError.getMessage());
            }

            throw e;
        }
    }

    /**
     * Scheduled cleanup of old cache files
     * Runs every 1 hour
     */
    @Scheduled(fixedRate = 3600000)
    public void cleanOldCacheFiles() {
        try {
            if (!Files.exists(cacheDir)) {
                log.debug("📂 Cache directory doesn't exist, skipping cleanup");
                return;
            }

            long deletedCount = Files.list(cacheDir)
                    .filter(this::isFileOlderThan2Hours)
                    .filter(this::deleteFile)
                    .count();

            if (deletedCount > 0) {
                log.info("🧹 Cleaned up {} old cache files", deletedCount);
            } else {
                log.debug("✅ No old cache files to clean");
            }

        } catch (IOException e) {
            log.error("❌ Error during cache cleanup: {}", e.getMessage(), e);
        }
    }

    /**
     * Checks if file is older than 2 hours
     */
    private boolean isFileOlderThan2Hours(Path path) {
        try {
            Instant lastModified = Files.getLastModifiedTime(path).toInstant();
            Instant threshold = Instant.now().minus(2, ChronoUnit.HOURS);
            return lastModified.isBefore(threshold);
        } catch (IOException e) {
            log.warn("⚠️ Failed to check modification time for {}: {}", path, e.getMessage());
            return false;
        }
    }

    /**
     * Deletes a file and logs the result
     */
    private boolean deleteFile(Path path) {
        try {
            boolean deleted = Files.deleteIfExists(path);
            if (deleted) {
                log.debug("🗑️ Deleted old cache: {}", path.getFileName());
            }
            return deleted;
        } catch (IOException e) {
            log.warn("⚠️ Failed to delete cache file {}: {}", path, e.getMessage());
            return false;
        }
    }

    /**
     * Manually clear all cache files (for admin/testing)
     */
    public void clearAllCache() {
        try {
            if (!Files.exists(cacheDir)) {
                log.info("📂 Cache directory doesn't exist, nothing to clear");
                return;
            }

            long deletedCount = Files.list(cacheDir)
                    .filter(this::deleteFile)
                    .count();

            log.info("🧹 Cleared entire cache: {} files deleted", deletedCount);

        } catch (IOException e) {
            log.error("❌ Error clearing cache: {}", e.getMessage(), e);
        }
    }

    /**
     * Get cache statistics
     */
    public CacheStats getCacheStats() {
        try {
            if (!Files.exists(cacheDir)) {
                return new CacheStats(0, 0L);
            }

            long[] stats = Files.list(cacheDir)
                    .mapToLong(path -> {
                        try {
                            return Files.size(path);
                        } catch (IOException e) {
                            return 0L;
                        }
                    })
                    .collect(
                            () -> new long[2], // [count, totalSize]
                            (arr, size) -> {
                                arr[0]++;
                                arr[1] += size;
                            },
                            (arr1, arr2) -> {
                                arr1[0] += arr2[0];
                                arr1[1] += arr2[1];
                            }
                    );

            return new CacheStats((int) stats[0], stats[1]);

        } catch (IOException e) {
            log.error("❌ Error getting cache stats: {}", e.getMessage());
            return new CacheStats(0, 0L);
        }
    }

    /**
     * Cache statistics data class
     */
    public record CacheStats(int fileCount, long totalSizeBytes) {
        public String getTotalSizeMB() {
            return String.format("%.2f MB", totalSizeBytes / (1024.0 * 1024.0));
        }
    }
}