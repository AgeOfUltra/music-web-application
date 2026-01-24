package com.music.musicwebapplication.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.music.musicwebapplication.dto.RequestSongDto;
import com.music.musicwebapplication.dto.SongUploadContainer;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.InputStream;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.*;

@Slf4j
@Service
public class AudioStreamService {

    private final S3Client client;
    private final SongRepo repo;
    private final SongRequestRepo songRequestRepo;
    private final ObjectMapper objectMapper;


    @Value("${aws.bucket.name}")
    private String bucketName;


    private final CacheManager cacheManager;


    @Autowired
    AudioStreamService(S3Client client, SongRepo repo, SongRequestRepo songRequestRepo, ObjectMapper objectMapper, CacheManager cacheManager){
        this.client = client;
        this.repo = repo;
        this.songRequestRepo = songRequestRepo;
        this.objectMapper = objectMapper;
        this.cacheManager = cacheManager;
        log.debug("AudioStreamService initialized with bucket: {}", bucketName);
    }

    @Transactional
    public void fileUploadHelper(SongUploadContainer container) throws Exception {
        log.info("Starting song upload process for: {}", container.getSongName());

        Optional<Song> currentSong = repo.findSongBySongName(container.getSongName());

        if(currentSong.isPresent()){
            log.warn("Song upload failed - song already exists in database with id: {}", currentSong.get().getId());
            return;
        }

        try{
            log.debug("Initiating file upload to S3 for song: {}", container.getSongName());
            String s3key = uploadFile(container.getFile());
            if(s3key.equals("Failed")){
                log.error("Song upload failed for: {}", container.getSongName());
                return;
            }
            log.debug("Song uploaded successfully to S3 with key: {}", s3key);

            String url = getStreamUrl(s3key);
            log.debug("Generated streaming URL: {}", url);

            SongDto uploadedSong = saveSong(container,url);
            log.debug("Song saved to database with name: {}", uploadedSong.getSongName());

            log.info("Song upload completed successfully: {}", container.getSongName());

        }catch (Exception e){
            log.error("Error during file upload process for song {}: {}", container.getSongName(), e.getMessage(), e);
            throw new Exception("Failed to upload song");
        }
    }
//    private String uploadFile(MultipartFile file) throws IOException{
//        String s3Key = Objects.requireNonNull(file.getOriginalFilename());
//        PutObjectResponse response = client.putObject(PutObjectRequest.builder()
//                .bucket(bucketName)
//                .key(file.getOriginalFilename())
//                .build(), RequestBody.fromBytes(file.getBytes())
//        );
//
//        if(response == null || response.eTag()== null){
//            throw new IOException("failed to upload the song");
//        }
//        return s3Key;
//    }

    private String uploadFile(MultipartFile file) {
        log.debug("Starting file upload to S3 bucket: {}", bucketName);
        String s3Key = Objects.requireNonNull(file.getOriginalFilename());
        log.debug("Uploading file with key: {}", s3Key);

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(s3Key)
                .contentType(file.getContentType())
                .build();

        try (InputStream is = file.getInputStream()) {
            log.debug("Putting object to S3 with size: {} bytes", file.getSize());
            client.putObject(
                    request,
                    RequestBody.fromInputStream(is, file.getSize())
            );
        }catch (Exception e){
            log.error("Failed to upload file to S3 bucket {}: {}", bucketName, e.getMessage(), e);
            return "Failed";
        }
        log.info("File uploaded successfully to S3: {}", s3Key);
        return s3Key;
    }


    // future implementation using the folders

//    private String uploadFile(MultipartFile file, String folderPath) throws IOException {
//        // Remove trailing slash if present
//        folderPath = folderPath.endsWith("/") ? folderPath : folderPath + "/";
//
//        String fileName = Objects.requireNonNull(file.getOriginalFilename());
//        String s3Key = folderPath + fileName;  // e.g., "album/mysong.mp3"
//
//        PutObjectResponse response = client.putObject(
//                PutObjectRequest.builder()
//                        .bucket(bucketName)
//                        .key(s3Key)
//                        .build(),
//                RequestBody.fromBytes(file.getBytes())
//        );
//
//        if(response == null || response.eTag() == null) {
//            throw new IOException("Failed to upload the song");
//        }
//
//        return s3Key;
//    }

    private String getStreamUrl(String fileName){
        log.debug("Generating stream URL for file: {}", fileName);
        return "/app/music/public/streamSong/"+fileName;
    }


    private void evictAllSongCaches() {
        log.debug("Starting cache eviction for all song caches");
        try {
            Cache allSongsCache = cacheManager.getCache("AllSongsPaged");
            Cache patternCache = cacheManager.getCache("CachedSongsPattern");
            Cache fileNamesCache = cacheManager.getCache("CachedFileNames");

            if (allSongsCache != null) {
                allSongsCache.clear();
                log.debug("Cleared cache: AllSongsPaged");
            }

            if (patternCache != null) {
                patternCache.clear();
                log.debug("Cleared cache: CachedSongsPattern");
            }

            if (fileNamesCache != null) {
                fileNamesCache.clear();
                log.debug("Cleared cache: CachedFileNames");
            }

            log.info("All song caches evicted successfully");
        } catch (Exception e) {
            log.error("Error evicting caches: {}", e.getMessage(), e);
        }
    }
    protected SongDto updateSongInDb(SongUploadContainer song, String url){
        log.debug("Updating song in database: {}", song.getSongName());
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


        Optional<Song> savedSong = Optional.of(repo.save(newSong));
        log.debug("Song saved to database with id: {}", savedSong.get().getId());

        evictAllSongCaches();

        log.info("Song successfully updated in database: {}", savedSong.get().getSongName());

        return toDto(savedSong.get());
    }

    @Retryable(
            retryFor = {SocketException.class, SocketTimeoutException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    @Caching(
            evict = {
                    @CacheEvict( value ="AllSongsPaged", allEntries = true),
                    @CacheEvict(value = "CachedSongsPattern",allEntries = true),
                    @CacheEvict(value = "CachedFileNames",allEntries = true)
            }
    ) // this method is for eviction purpose only.
    public SongDto saveSong(SongUploadContainer song, String url){
        log.debug("Saving song with cache eviction: {}", song.getSongName());
        return updateSongInDb(song,url);
    }



    @Cacheable(value = "AllSongsPaged", key = "{#page, #size}")
    public List<SongDto> getAllSongsName(int page, int size) {
        log.debug("Fetching all songs - page: {}, size: {}", page, size);
        Pageable pageable = PageRequest.of(page, size, Sort.by("songName"));
        List<SongDto> songs = repo.findAll(pageable)
                .getContent()
                .stream()
                .map(this::toDto)
                .toList();
        log.info("Retrieved {} songs for page {}", songs.size(), page);
        return songs;
    }


    // Add this method
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

    @Cacheable(
            value = "CachedSongsPattern",
            key = "#prefix.toLowerCase().split(' ')[0]"
    )
    public List<SongDto> searchSongsByName(String prefix) {
        log.debug("Searching songs by name prefix: {}", prefix);
        List<SongDto> results = repo.findBySongNameContainingIgnoreCase(prefix).stream().map(s -> objectMapper.convertValue(s, SongDto.class)).toList();
        log.info("Found {} songs matching prefix: {}", results.size(), prefix);
        return results;
    }

    @Cacheable(value = "CachedFileNames")
    public List<String> getSongList() {
        log.debug("Fetching all song file names");
        List<String> fileNames = repo.getSongsByFileName();
        log.info("Retrieved {} song file names", fileNames.size());
        return fileNames;
    }

    public String requestedSongSave(RequestSongDto song){
        log.info("Saving song request: {}", song.getSongName());

        RequestSong newSong = objectMapper.convertValue(song, RequestSong.class);
        newSong.setStatus(Status.SENT);

        try{
            songRequestRepo.save(newSong);
            log.info("Song request saved successfully: {}", song.getSongName());
        }catch (Exception s){
            log.error("Error while saving requested song {}: {}", song.getSongName(), s.getMessage(), s);
            return "Failed";
        }
        return "Saved";
    }

    public Optional<List<RequestSongDto>> getAllRequestStatusSong(Status status){
        log.debug("Fetching all song requests with status: {}", status);

        Optional<List<RequestSongDto>> result = Optional.of(songRequestRepo.findRequestSongByStatus(status)
                .map(songs -> songs.stream()
                        .map(song -> objectMapper.convertValue(song, RequestSongDto.class))
                        .toList())
                .orElse(Collections.emptyList()));

        log.info("Retrieved {} song requests with status: {}", result.get().size(), status);
        return result;
    }

    // admin song status update service method
    public String updateStatusForRequestSong(String songName, Status newStatus,String note){
        log.info("Updating status for requested song: {} to status: {}", songName, newStatus);
        Optional<RequestSong> currentSong = songRequestRepo.findRequestSongBySongName(songName);

        if(currentSong.isEmpty()){
            log.warn("Song not found for status update: {}", songName);
            return "Song Not found";
        }
        try{
            currentSong.get().setStatus(newStatus);
            currentSong.get().setNote(note);
            songRequestRepo.save(currentSong.get());
            log.info("Successfully updated status for song: {} to {}", songName, newStatus);
        }catch (Exception e){
            log.error("Error while updating requested song {}: {}", songName, e.getMessage(), e);
            return "Failed";
        }

        return "updated";
    }

    public Optional<List<RequestSongDto>> getAllSongForRequestor(String requestor){
        log.debug("Fetching all songs for requestor: {}", requestor);
        Optional<List<RequestSongDto>> result = Optional.of(repo.findSongsByRequestor(requestor)
                .map(songs -> songs.stream()
                        .map(song -> objectMapper.convertValue(song, RequestSongDto.class))
                        .toList())
                .orElse(Collections.emptyList()));
        log.info("Retrieved {} songs for requestor: {}", result.get().size(), requestor);
        return result;
    }

    public boolean checkSongRequestAvailable(String songName){
        log.debug("Checking if song request is available: {}", songName);
        Optional<RequestSong> currentSong = songRequestRepo.findRequestSongBySongName(songName);

        boolean isAvailable = currentSong.isPresent() && currentSong.get().getStatus().equals(Status.SENT);
        log.debug("Song request availability for {}: {}", songName, isAvailable);
        return isAvailable;
    }
}