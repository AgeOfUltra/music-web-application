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

    // ---------------- INIT ----------------

    @PostConstruct
    public void init() throws IOException {
        cacheDir = Paths.get(cacheDirPath).normalize();
        Files.createDirectories(cacheDir);
        log.info("📂 Song cache initialized at {}", cacheDir.toAbsolutePath());
    }

    // ---------------- PUBLIC API ----------------

    /**
     * Returns cached song resource or downloads it if missing
     */
    public Resource getCachedResource(String objectKey) throws IOException {
        validateFileName(objectKey);

        Path cachedFile = cacheDir.resolve(objectKey);

        // Touch file to prevent cleanup during streaming
        if (Files.exists(cachedFile)) {
            Files.setLastModifiedTime(cachedFile, FileTime.from(Instant.now()));
            return new FileSystemResource(cachedFile);
        }

        // Ensure song exists in DB
        Optional<Song> song = songRepo.findSongByFileName(objectKey);
        if (song.isEmpty()) {
            throw new SongNotFoundException("Song not found: " + objectKey);
        }

        downloadFromS3WithRetry(objectKey, cachedFile);

        Files.setLastModifiedTime(cachedFile, FileTime.from(Instant.now()));
        return new FileSystemResource(cachedFile);
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

        log.info("⬇️ Downloading {} from S3", objectKey);

        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(objectKey)
                .build();

        try (ResponseInputStream<GetObjectResponse> s3Stream = s3Client.getObject(request);
             OutputStream out = Files.newOutputStream(
                     targetFile,
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

        } catch (SocketException | SocketTimeoutException e) {
            cleanupPartialFile(targetFile);
            log.warn("⚠️ Network error downloading {}, retrying", objectKey);
            throw e;
        } catch (IOException e) {
            cleanupPartialFile(targetFile);
            log.error("❌ IO error downloading {}: {}", objectKey, e.getMessage());
            throw e;
        }
    }

    // ---------------- CLEANUP ----------------

    /**
     * Cleans cache files older than 2 hours
     */
    @Scheduled(fixedRate = 3600000)
    public void cleanupOldFiles() {
        try {
            if (!Files.exists(cacheDir)) return;

            long removed = Files.list(cacheDir)
                    .filter(this::isOlderThan2Hours)
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

    private boolean isOlderThan2Hours(Path file) {
        try {
            Instant modified = Files.getLastModifiedTime(file).toInstant();
            return modified.isBefore(Instant.now().minus(2, ChronoUnit.HOURS));
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

}