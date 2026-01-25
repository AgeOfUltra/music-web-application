package com.music.musicwebapplication.service;

import com.music.musicwebapplication.entity.Song;
import com.music.musicwebapplication.exception.SongNotFoundException;
import com.music.musicwebapplication.repo.SongRepo;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.io.IOException;
import java.io.OutputStream;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Service
@RequiredArgsConstructor
@Slf4j
public class SongCacheService {

    private final S3Client s3Client;
    private final SongRepo songRepo;

    @Value("${aws.bucket.name}")
    private String bucketName;

    @Value("${app.cache-dir:${user.dir}/song-cache}")
    private String cacheDirPath;

    private Path cacheDir;

    // Track download status: DOWNLOADING, DOWNLOADED, FAILED
    private final ConcurrentHashMap<String, DownloadStatus> downloadStatus = new ConcurrentHashMap<>();

    // Track ongoing download futures to prevent duplicate downloads
    private final ConcurrentHashMap<String, CompletableFuture<Resource>> ongoingDownloads = new ConcurrentHashMap<>();

    // Per-file locks for synchronized access
    private final ConcurrentHashMap<String, Lock> fileLocks = new ConcurrentHashMap<>();

    // Download status enum
    private enum DownloadStatus {
        DOWNLOADING,
        DOWNLOADED,
        FAILED
    }

    // ---------------- INIT ----------------

    @PostConstruct
    public void init() throws IOException {
        cacheDir = Paths.get(cacheDirPath).normalize();
        Files.createDirectories(cacheDir);
        log.debug("📂 Song cache initialized at {}", cacheDir.toAbsolutePath());
    }

    // ---------------- PUBLIC API ----------------

    /**
     * Returns cached song resource or downloads it if missing
     * Multiple concurrent requests for the same song will share the same download
     */
    @Async("asyncTaskExecutor")
    public CompletableFuture<Resource> getCachedResource(String objectKey) {
        try {
            validateFileName(objectKey);
            Path cachedFile = cacheDir.resolve(objectKey);

            // Quick check: if file exists and is valid, return immediately
            if (isFileValidAndExists(cachedFile)) {
                touchFile(cachedFile);
                log.debug("📦 Cache HIT: {}", objectKey);
                return CompletableFuture.completedFuture(new FileSystemResource(cachedFile));
            }

            // Check if download is already in progress
            CompletableFuture<Resource> existingDownload = ongoingDownloads.get(objectKey);
            if (existingDownload != null) {
                log.info("⏳ Download already in progress for: {}. Waiting...", objectKey);
                return existingDownload;
            }

            // Start new download
            CompletableFuture<Resource> downloadFuture = CompletableFuture.supplyAsync(() -> {
                Lock lock = fileLocks.computeIfAbsent(objectKey, k -> new ReentrantLock());
                lock.lock();

                try {
                    // Double-check file existence after acquiring lock
                    if (isFileValidAndExists(cachedFile)) {
                        log.info("📦 File became available while waiting: {}", objectKey);
                        touchFile(cachedFile);
                        return new FileSystemResource(cachedFile);
                    }

                    log.info("⬇️ Cache MISS: Starting download for {}", objectKey);

                    // Verify song exists in database
                    Optional<Song> song = songRepo.findSongByFileName(objectKey);
                    if (song.isEmpty()) {
                        throw new SongNotFoundException("Song not found in database: " + objectKey);
                    }

                    // Download from S3
                    downloadFromS3WithRetry(objectKey, cachedFile);

                    // Verify download success
                    if (!isFileValidAndExists(cachedFile)) {
                        throw new IOException("Downloaded file is invalid: " + objectKey);
                    }

                    touchFile(cachedFile);
                    downloadStatus.put(objectKey, DownloadStatus.DOWNLOADED);

                    log.debug("✅ Successfully cached: {} (size: {} bytes)",
                            objectKey, Files.size(cachedFile));

                    return new FileSystemResource(cachedFile);

                } catch (Exception e) {
                    downloadStatus.put(objectKey, DownloadStatus.FAILED);
                    cleanupPartialFile(cachedFile);
                    log.error("❌ Failed to download {}: {}", objectKey, e.getMessage());
                    throw new RuntimeException("Failed to cache: " + objectKey, e);

                } finally {
                    lock.unlock();
                    cleanupLockIfUnused(objectKey, lock);
                }
            });

            // Store in ongoing downloads
            ongoingDownloads.put(objectKey, downloadFuture);

            // Remove from ongoing when complete
            downloadFuture.whenComplete((result, error) -> {
                ongoingDownloads.remove(objectKey);
            });

            return downloadFuture;

        } catch (Exception e) {
            log.error("❌ Error getting cached resource for {}: {}", objectKey, e.getMessage());
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Auto-download song for background processing (e.g., favorites)
     * Returns true if download started/completed, false if song doesn't exist
     */
    public boolean songAutoDownload(String objectKey) {
        try {
            validateFileName(objectKey);
            Path cachedFile = cacheDir.resolve(objectKey);

            // Check if already downloaded or in progress
            DownloadStatus status = downloadStatus.get(objectKey);
            if ((status == DownloadStatus.DOWNLOADED || status == DownloadStatus.DOWNLOADING)) {
                if (isFileValidAndExists(cachedFile)) {
                    touchFile(cachedFile);
                    log.debug("📦 Song already cached or downloading: {}", objectKey);
                    return true;
                }
            }

            // Check if file already exists
            if (isFileValidAndExists(cachedFile)) {
                touchFile(cachedFile);
                downloadStatus.put(objectKey, DownloadStatus.DOWNLOADED);
                log.debug("📦 Song already exists in cache: {}", objectKey);
                return true;
            }

            // Verify song exists in database
            Optional<Song> song = songRepo.findSongByFileName(objectKey);
            if (song.isEmpty()) {
                log.warn("⚠️ Song not found in database: {}", objectKey);
                return false;
            }

            // Get or create lock for this file
            Lock lock = fileLocks.computeIfAbsent(objectKey, k -> new ReentrantLock());

            // Try to acquire lock (non-blocking)
            if (!lock.tryLock()) {
                log.info("⏳ Download already in progress for: {}", objectKey);
                return true; // Another thread is downloading
            }

            try {
                // Double-check after acquiring lock
                if (isFileValidAndExists(cachedFile)) {
                    touchFile(cachedFile);
                    downloadStatus.put(objectKey, DownloadStatus.DOWNLOADED);
                    return true;
                }

                // Download from S3
                downloadFromS3WithRetry(objectKey, cachedFile);

                // Verify download success
                if (isFileValidAndExists(cachedFile)) {
                    touchFile(cachedFile);
                    downloadStatus.put(objectKey, DownloadStatus.DOWNLOADED);
                    log.info("✅ Auto-downloaded: {}", objectKey);
                    return true;
                } else {
                    downloadStatus.put(objectKey, DownloadStatus.FAILED);
                    return false;
                }

            } catch (Exception e) {
                downloadStatus.put(objectKey, DownloadStatus.FAILED);
                cleanupPartialFile(cachedFile);
                log.error("❌ Auto-download failed for {}: {}", objectKey, e.getMessage());
                return false;

            } finally {
                lock.unlock();
                cleanupLockIfUnused(objectKey, lock);
            }

        } catch (Exception e) {
            log.error("❌ Error in auto-download for {}: {}", objectKey, e.getMessage());
            return false;
        }
    }

    // ---------------- S3 DOWNLOAD ----------------

    /**
     * Downloads song from S3 with retry on network failures only
     */
    @Retryable(
            retryFor = {SocketException.class, SocketTimeoutException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    protected void downloadFromS3WithRetry(String objectKey, Path targetFile) throws IOException {
        downloadStatus.put(objectKey, DownloadStatus.DOWNLOADING);

        log.debug("⬇️ Downloading {} from S3", objectKey);

        // Download to temporary file first
        Path tempFile = targetFile.getParent().resolve(objectKey + ".tmp");

        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(objectKey)
                .build();

        try (ResponseInputStream<GetObjectResponse> s3Stream = s3Client.getObject(request);
             OutputStream out = Files.newOutputStream(
                     tempFile,
                     StandardOpenOption.CREATE,
                     StandardOpenOption.TRUNCATE_EXISTING)) {

            byte[] buffer = new byte[8192];
            int read;
            long total = 0;

            while ((read = s3Stream.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                total += read;
            }

            log.info("✅ Downloaded {} bytes for {}", total, objectKey);

            // Atomic move from temp to final location
            Files.move(tempFile, targetFile,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);

            downloadStatus.put(objectKey, DownloadStatus.DOWNLOADED);

        } catch (SocketException | SocketTimeoutException e) {
            downloadStatus.put(objectKey, DownloadStatus.FAILED);
            cleanupPartialFile(tempFile);
            cleanupPartialFile(targetFile);
            log.warn("⚠️ Network error downloading {}, retrying", objectKey);
            throw e;

        } catch (IOException e) {
            downloadStatus.put(objectKey, DownloadStatus.FAILED);
            cleanupPartialFile(tempFile);
            cleanupPartialFile(targetFile);
            log.error("❌ IO error downloading {}: {}", objectKey, e.getMessage());
            throw e;
        }
    }

    // ---------------- CLEANUP ----------------

    /**
     * Cleans cache files older than 2 hours
     */
    @Scheduled(fixedRate = 3600000) // Every hour
    public void cleanupOldFiles() {
        try {
            if (!Files.exists(cacheDir)) return;

            long removed = Files.list(cacheDir)
                    .filter(path -> path.toString().endsWith(".mp3")) // Only mp3 files
                    .filter(this::isOlderThan1Hours)
                    .peek(path -> {
                        String fileName = path.getFileName().toString();
                        downloadStatus.remove(fileName);
                        fileLocks.remove(fileName);
                    })
                    .filter(this::deleteFile)
                    .count();

            if (removed > 0) {
                log.info("🧹 Removed {} stale cache files", removed);
            }

        } catch (IOException e) {
            log.error("❌ Cache cleanup failed", e);
        }
    }

    // ---------------- HELPERS ----------------

    /**
     * Check if file exists and is valid
     */
    private boolean isFileValidAndExists(Path file) {
        try {
            return Files.exists(file) &&
                    Files.isRegularFile(file) &&
                    Files.size(file) > 0 &&
                    Files.isReadable(file);
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Update file's last modified time
     */
    private void touchFile(Path file) {
        try {
            Files.setLastModifiedTime(file, FileTime.from(Instant.now()));
        } catch (IOException e) {
            log.warn("⚠️ Failed to update last modified time for {}", file.getFileName());
        }
    }

    private boolean isOlderThan1Hours(Path file) {
        try {
            Instant modified = Files.getLastModifiedTime(file).toInstant();
            return modified.isBefore(Instant.now().minus(1, ChronoUnit.HOURS));
        } catch (IOException e) {
            return false;
        }
    }

    private boolean deleteFile(Path file) {
        try {
            return Files.deleteIfExists(file);
        } catch (IOException e) {
            return false;
        }
    }

    private void cleanupPartialFile(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
        }
    }

    /**
     * Remove lock if no longer needed to prevent memory leak
     */
    private void cleanupLockIfUnused(String key, Lock lock) {
        if (lock instanceof ReentrantLock reentrantLock) {
            if (!reentrantLock.hasQueuedThreads() && !reentrantLock.isLocked()) {
                fileLocks.remove(key);
            }
        }
    }

    /**
     * Prevents path traversal & invalid names
     */
    private void validateFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("File name is empty");
        }

        // Prevent path traversal
        if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
            throw new IllegalArgumentException("Invalid file name");
        }

        // Allow spaces + safe characters
        if (!fileName.matches("^[a-zA-Z0-9 ._\\-]+\\.mp3$")) {
            throw new IllegalArgumentException("Invalid file name");
        }
    }

    // ---------------- MONITORING ----------------

    /**
     * Get current cache statistics
     */
    public CacheStats getCacheStats() {
        long downloading = downloadStatus.values().stream()
                .filter(status -> status == DownloadStatus.DOWNLOADING)
                .count();

        long downloaded = downloadStatus.values().stream()
                .filter(status -> status == DownloadStatus.DOWNLOADED)
                .count();

        return new CacheStats(
                ongoingDownloads.size(),
                fileLocks.size(),
                downloading,
                downloaded,
                downloadStatus.size()
        );
    }

    public record CacheStats(
            int ongoingDownloads,
            int activeLocks,
            long downloading,
            long downloaded,
            int totalTracked
    ) {}
}