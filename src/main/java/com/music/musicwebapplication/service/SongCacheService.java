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
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class SongCacheService {


    private final S3Client client;
    private final SongRepo repo;

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String SONG_CACHE_KEY = "song:list";


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
        log.info("Song cache directory initialized at: {}", cacheDir.toAbsolutePath());
    }

    @PostConstruct
    public void loadOnStartup() {
        log.info("cache initial request");
        updateCache();
    }

    @Cacheable(value = "songCacheMetadata", key = "#objectKey")
    public String cacheSongIfNeeded(String objectKey) throws IOException {
        if (!Files.exists(cacheDir)) {
            Files.createDirectories(cacheDir);
        }

        Path cachedFile = cacheDir.resolve(objectKey);

        if (Files.exists(cachedFile)) {
            log.info(" Song found in local cache: {} at {}", objectKey, cachedFile.toAbsolutePath());
            return cachedFile.toAbsolutePath().toString();
        }

        log.info("Downloading song {} from S3", objectKey);
        Optional<Song> song = repo.findSongByFileName(objectKey);
        if (song.isEmpty()) {
            throw new SongNotFoundException("Song not found in DB: " + objectKey);
        }

        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(objectKey)
                .build();

        try (ResponseInputStream<GetObjectResponse> s3Stream = client.getObject(request);
             OutputStream out = Files.newOutputStream(cachedFile)) {
            s3Stream.transferTo(out);
        }

        log.info(" Cached {} locally at {}", objectKey, cachedFile);
        return cachedFile.toAbsolutePath().toString();
    }

    public Resource getCachedResource(String objectKey) throws IOException {
        String cachedPath = cacheSongIfNeeded(objectKey);
        return new FileSystemResource(cachedPath);
    }

    @Scheduled(fixedRate = 3600000) // every 1 hour
    public void cleanOldCacheFiles() throws IOException {
        if (Files.exists(cacheDir)) {
            Files.list(cacheDir)
                    .filter(p -> {
                        try {
                            return Files.getLastModifiedTime(p)
                                    .toInstant()
                                    .isBefore(Instant.now().minus(2, ChronoUnit.HOURS));
                        } catch (IOException e) {
                            return false;
                        }
                    })
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                            log.info(" Deleted old cache: {}", p);
                        } catch (IOException e) {
                            log.warn(" Failed to delete {}", p);
                        }
                    });
        }
    }

    @Scheduled(cron = "0 0 */1 * * *")
    public void refreshSongCache() {
       updateCache();
    }
    // ---------- Fetch From Cache ----------
    public List<String> getSongList() {
        log.info("cache hit for cache songs");
        Object cached = redisTemplate.opsForValue().get(SONG_CACHE_KEY);
        return cached != null ? (List<String>) cached : new ArrayList<>();
    }

    // ---------- Update Cache ----------
    private void updateCache() {
        log.info("Db call for cache.");
        List<String> latestSongs = repo.findAllSongBySongName(); // DB call
        redisTemplate.opsForValue().set(SONG_CACHE_KEY, latestSongs);
        System.out.println("♻️ Redis Cache Updated — " + latestSongs.size() + " songs");
    }
}
