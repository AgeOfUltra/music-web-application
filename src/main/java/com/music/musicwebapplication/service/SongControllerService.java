package com.music.musicwebapplication.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.music.musicwebapplication.dto.RequestSongDto;
import com.music.musicwebapplication.dto.SongContainer;
import com.music.musicwebapplication.dto.SongDto;
import com.music.musicwebapplication.entity.RequestSong;
import com.music.musicwebapplication.entity.Song;
import com.music.musicwebapplication.repo.SongRepo;
import com.music.musicwebapplication.repo.SongRequestRepo;
import com.music.musicwebapplication.enums.Status;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.*;

@Slf4j
@Service
public class SongControllerService {

    private final S3Client client;
    private final SongRepo repo;
    private final SongRequestRepo songRequestRepo;
    private final ObjectMapper objectMapper;
    private final CacheManager cacheManager;

    @Value("${aws.bucket.name}")
    private String bucketName;

    @Autowired
    SongControllerService(S3Client client, SongRepo repo, SongRequestRepo songRequestRepo,
                          ObjectMapper objectMapper, CacheManager cacheManager) {
        this.client = client;
        this.repo = repo;
        this.songRequestRepo = songRequestRepo;
        this.objectMapper = objectMapper;
        this.cacheManager = cacheManager;
    }

    /**
     * Main file upload handler with comprehensive error handling
     */
    public String fileUploadHelper(SongContainer container) throws Exception {
        log.info("📤 Song uploading process started for: {}", container.getSongName());

        // Check if song already exists
        Optional<Song> currentSong = repo.findSongBySongName(container.getSongName());
        if (currentSong.isPresent()) {
            log.warn("⚠️ Song already exists in database with id: {}", currentSong.get().getId());
            return "Song already exists";
        }

        try {
            // Upload to S3 with retry logic
            String s3key = uploadFileWithRetry(container.getFile());
            log.info("✅ Song uploaded successfully to S3 with key: {}", s3key);

            // Generate streaming URL
            String url = getStreamUrl(s3key);
            log.debug("🔗 Generated URL: {}", url);

            // Save to database and evict cache
            SongDto uploadedSong = saveSong(container, url);
            log.info("💾 Song saved to database: {}", uploadedSong.getSongName());

            return "Song uploaded and saved successfully";

        } catch (IOException e) {
            log.error("❌ IO error during file upload: {}", e.getMessage(), e);
            throw new Exception("Failed to upload song due to IO error: " + e.getMessage());
        } catch (S3Exception e) {
            log.error("❌ S3 error during file upload: {} (Status: {})",
                    e.getMessage(), e.statusCode(), e);
            throw new Exception("Failed to upload song to S3: " + e.awsErrorDetails().errorMessage());
        } catch (Exception e) {
            log.error("❌ Unexpected error during file upload: {}", e.getMessage(), e);
            throw new Exception("Failed to upload song: " + e.getMessage());
        }
    }

    /**
     * Uploads file with retry logic wrapper
     */
    private String uploadFileWithRetry(MultipartFile file) throws IOException {
        String fileName = Objects.requireNonNull(file.getOriginalFilename());
        log.debug("📦 Preparing to upload file: {} (size: {} bytes)", fileName, file.getSize());

        try {
            return uploadFile(file);
        } catch (Exception e) {
            log.error("❌ All upload attempts failed for {}: {}", fileName, e.getMessage());
            throw e;
        }
    }

    /**
     * Uploads file to S3 with automatic retry on network errors
     * Retries up to 5 times with longer delays for upload operations
     */
    @Retryable(
            retryFor = {
                    SocketException.class,
                    SocketTimeoutException.class,
                    SdkClientException.class
            },
            maxAttempts = 5,
            backoff = @Backoff(
                    delay = 2000,      // Start with 2 seconds
                    multiplier = 2.0,  // Double each time
                    maxDelay = 30000   // Max 30 seconds between retries
            )
    )
    private String uploadFile(MultipartFile file) throws IOException {
        String s3Key = Objects.requireNonNull(file.getOriginalFilename());

        log.debug("📤 Uploading to S3: bucket={}, key={}", bucketName, s3Key);

        try {
            byte[] fileBytes = file.getBytes();

            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .contentType(file.getContentType())
                    .contentLength((long) fileBytes.length)
                    .build();

            PutObjectResponse response = client.putObject(
                    putRequest,
                    RequestBody.fromBytes(fileBytes)
            );

            if (response == null || response.eTag() == null) {
                throw new IOException("Failed to upload song - no ETag received");
            }

            log.debug("✅ Upload successful - ETag: {}", response.eTag());
            return s3Key;

        } catch (SocketException | SocketTimeoutException e) {
            log.warn("⚠️ Network error uploading {}, will retry: {}", s3Key, e.getMessage());
            throw e; // Trigger retry

        } catch (SdkClientException e) {
            log.warn("⚠️ AWS SDK error uploading {}, will retry: {}", s3Key, e.getMessage());
            throw e; // Trigger retry

        } catch (S3Exception e) {
            log.error("❌ S3 service error uploading {}: {} (Status: {})",
                    s3Key, e.awsErrorDetails().errorMessage(), e.statusCode());
            throw new IOException("S3 upload failed: " + e.awsErrorDetails().errorMessage(), e);

        } catch (IOException e) {
            log.error("❌ IO error uploading {}: {}", s3Key, e.getMessage());
            throw e;
        }
    }

    /**
     * Future implementation using folders
     * Also includes retry logic
     */
//    @Retryable(
//            retryFor = {
//                    SocketException.class,
//                    SocketTimeoutException.class,
//                    SdkClientException.class
//            },
//            maxAttempts = 5,
//            backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 30000)
//    )
//    private String uploadFileToFolder(MultipartFile file, String folderPath) throws IOException {
//        // Ensure folder path formatting
//        folderPath = folderPath.endsWith("/") ? folderPath : folderPath + "/";
//
//        String fileName = Objects.requireNonNull(file.getOriginalFilename());
//        String s3Key = folderPath + fileName;  // e.g., "songs/approved/mysong.mp3"
//
//        log.debug("📤 Uploading to S3 folder: bucket={}, key={}", bucketName, s3Key);
//
//        try {
//            byte[] fileBytes = file.getBytes();
//
//            PutObjectRequest putRequest = PutObjectRequest.builder()
//                    .bucket(bucketName)
//                    .key(s3Key)
//                    .contentType(file.getContentType())
//                    .contentLength((long) fileBytes.length)
//                    .build();
//
//            PutObjectResponse response = client.putObject(
//                    putRequest,
//                    RequestBody.fromBytes(fileBytes)
//            );
//
//            if (response == null || response.eTag() == null) {
//                throw new IOException("Failed to upload song - no ETag received");
//            }
//
//            log.debug("✅ Upload successful to folder - ETag: {}", response.eTag());
//            return s3Key;
//
//        } catch (SocketException | SocketTimeoutException | SdkClientException e) {
//            log.warn("⚠️ Network error uploading {} to folder,  will retry: {} ", s3Key, e.getMessage());
//            throw e; // Trigger retry
//
//        } catch (S3Exception e) {
//            log.error("❌ S3 service error uploading {} to folder: {}",
//                    s3Key, e.awsErrorDetails().errorMessage());
//            throw new IOException("S3 upload failed: " + e.awsErrorDetails().errorMessage(), e);
//        }
//    }

    /**
     * Generates streaming URL
     */
    private String getStreamUrl(String fileName) {
        return "/app/music/public/streamSong/" + fileName;
    }

    /**
     * Evicts all song-related caches
     */
    private void evictAllSongCaches() {
        try {
            Cache allSongsCache = cacheManager.getCache("AllSongsPaged");
            Cache patternCache = cacheManager.getCache("CachedSongsPattern");
            Cache fileNamesCache = cacheManager.getCache("CachedFileNames");

            if (allSongsCache != null) {
                allSongsCache.clear();
                log.debug("🗑️ Cleared cache: AllSongsPaged");
            }

            if (patternCache != null) {
                patternCache.clear();
                log.debug("🗑️ Cleared cache: CachedSongsPattern");
            }

            if (fileNamesCache != null) {
                fileNamesCache.clear();
                log.debug("🗑️ Cleared cache: CachedFileNames");
            }

            log.info("✅ All song caches evicted successfully");
        } catch (Exception e) {
            log.error("❌ Error evicting caches: {}", e.getMessage(), e);
        }
    }

    /**
     * Updates song in database
     */
    protected SongDto updateSongInDb(SongContainer song, String url) {
        Song newSong = new Song();
        newSong.setSongName(song.getSongName());
        newSong.setFileName(song.getFileName());
        newSong.setHero(song.getHero());
        newSong.setHeroine(song.getHeroine());
        newSong.setUrl(url);
        newSong.setSinger(song.getSinger());
        newSong.setMovie(song.getMovie());
        newSong.setLanguage(song.getLanguage());
        newSong.setSongType(song.getSongType());

        Song savedSong = repo.save(newSong);
        evictAllSongCaches();

        log.debug("💾 Saved Song: {}", savedSong.getSongName());

        return toDto(savedSong);
    }

    /**
     * Saves song with cache eviction
     */
    @Caching(
            evict = {
                    @CacheEvict(value = "AllSongsPaged", allEntries = true),
                    @CacheEvict(value = "CachedSongsPattern", allEntries = true),
                    @CacheEvict(value = "CachedFileNames", allEntries = true)
            }
    )
    public SongDto saveSong(SongContainer song, String url) {
        return updateSongInDb(song, url);
    }

    /**
     * Gets paginated list of songs with caching
     */
    @Cacheable(value = "AllSongsPaged", key = "{#page, #size}")
    public List<SongDto> getAllSongsName(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("songName"));
        return repo.findAll(pageable)
                .getContent()
                .stream()
                .map(this::toDto)
                .toList();
    }

    /**
     * Converts Song entity to DTO
     */
    private SongDto toDto(Song song) {
        SongDto dto = new SongDto();
        dto.setSongName(song.getSongName());
        dto.setFileName(song.getFileName());
        dto.setMovie(song.getMovie());
        dto.setSinger(song.getSinger());
        dto.setSongType(song.getSongType());
        dto.setHero(song.getHero());
        dto.setHeroine(song.getHeroine());
        dto.setLanguage(song.getLanguage());
        return dto;
    }

    /**
     * Searches songs by name with caching
     */
    @Cacheable(
            value = "CachedSongsPattern",
            key = "#prefix.toLowerCase().substring(0, #prefix.length() > 2 ? 2 : #prefix.length())"
    )
    public List<SongDto> searchSongsByName(String prefix) {
        log.debug("🔍 Database query for prefix: {}", prefix);
        return repo.findBySongNameContainingIgnoreCase(prefix)
                .stream()
                .map(s -> objectMapper.convertValue(s, SongDto.class))
                .toList();
    }

    /**
     * Gets list of song filenames with caching
     */
    @Cacheable(value = "CachedFileNames")
    public List<String> getSongList() {
        log.debug("📋 Fetching all song filenames");
        return repo.getSongsByFileName();
    }

    /**
     * Saves a requested song
     */
    public String requestedSongSave(RequestSongDto song) {
        RequestSong newSong = objectMapper.convertValue(song, RequestSong.class);
        newSong.setStatus(Status.SENT);

        try {
            songRequestRepo.save(newSong);
            log.info("✅ Saved song request: {}", song.getSongName());
            return "Saved";
        } catch (Exception e) {
            log.error("❌ Error saving requested song: {}", e.getMessage(), e);
            return "Failed";
        }
    }

    /**
     * Gets all song requests by status
     */
    public Optional<List<RequestSongDto>> getAllRequestStatusSong(Status status) {
        return Optional.of(songRequestRepo.findRequestSongByStatus(status)
                .map(songs -> songs.stream()
                        .map(song -> objectMapper.convertValue(song, RequestSongDto.class))
                        .toList())
                .orElse(Collections.emptyList()));
    }

    /**
     * Updates status for a song request (admin function)
     */
    public String updateStatusForRequestSong(String songName, Status newStatus, String note) {
        Optional<RequestSong> currentSong = songRequestRepo.findRequestSongBySongName(songName);

        if (currentSong.isEmpty()) {
            log.warn("⚠️ Song request not found: {}", songName);
            return "Song Not found";
        }

        try {
            RequestSong song = currentSong.get();
            song.setStatus(newStatus);
            song.setNote(note);
            songRequestRepo.save(song);

            log.info("✅ Updated song request status: {} -> {}", songName, newStatus);
            return "updated";
        } catch (Exception e) {
            log.error("❌ Error updating requested song: {}", e.getMessage(), e);
            return "Failed";
        }
    }

    /**
     * Gets all songs for a specific requestor
     */
    public Optional<List<RequestSongDto>> getAllSongForRequestor(String requestor) {
        return Optional.of(repo.findSongsByRequestor(requestor)
                .map(songs -> songs.stream()
                        .map(song -> objectMapper.convertValue(song, RequestSongDto.class))
                        .toList())
                .orElse(Collections.emptyList()));
    }

    /**
     * Checks if a song request is available and pending
     */
    public boolean checkSongRequestAvailable(String songName) {
        Optional<RequestSong> currentSong = songRequestRepo.findRequestSongBySongName(songName);
        return currentSong.isPresent() && currentSong.get().getStatus().equals(Status.SENT);
    }
}