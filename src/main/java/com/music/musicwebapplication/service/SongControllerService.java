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
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

@Slf4j
@Service
public class SongControllerService {

    private final S3Client client;
    private final SongRepo repo;
    private final SongRequestRepo songRequestRepo;
    private final ObjectMapper objectMapper;


    @Value("${aws.bucket.name}")
    private String bucketName;


    private final CacheManager cacheManager;


    @Autowired
    SongControllerService(S3Client client, SongRepo repo, SongRequestRepo songRequestRepo, ObjectMapper objectMapper, CacheManager cacheManager){
        this.client = client;
        this.repo = repo;
        this.songRequestRepo = songRequestRepo;
        this.objectMapper = objectMapper;
        this.cacheManager = cacheManager;
    }

    public String fileUploadHelper(SongContainer container) throws Exception {
        log.info("Song uploading process started");

        Optional<Song> currentSong = repo.findSongBySongName(container.getSongName());

        if(currentSong.isPresent()){
            log.info("Song uploading failed! because song already exist in data base with id {}",currentSong.get().getId());
            return "Song already exist";
        }

        try{
            String s3key = uploadFile(container.getFile());
            log.info("Song uploaded successfully with key {}",s3key);

            String url = getStreamUrl(s3key);
            log.info("Generated URL: {}", url);

            SongDto uploadedSong = saveSong(container,url);
            log.info("song saved with name {}",uploadedSong.getSongName());

            return "Song uploaded and saved successfully";

        }catch (Exception e){
            log.error("Error during file upload process: {}", e.getMessage(), e);
            throw new Exception("Failed to upload song: " + e.getMessage());
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

    private String uploadFile(MultipartFile file) throws IOException {

        String s3Key = Objects.requireNonNull(file.getOriginalFilename());

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(s3Key)
                .contentType(file.getContentType())
                .build();

        try (InputStream is = file.getInputStream()) {
            client.putObject(
                    request,
                    RequestBody.fromInputStream(is, file.getSize())
            );
        }

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
        return "/app/music/public/streamSong/"+fileName;
    }


    private void evictAllSongCaches() {
        try {
            Cache allSongsCache = cacheManager.getCache("AllSongsPaged");
            Cache patternCache = cacheManager.getCache("CachedSongsPattern");
            Cache fileNamesCache = cacheManager.getCache("CachedFileNames");

            if (allSongsCache != null) {
                allSongsCache.clear();
                log.info("🗑️ Cleared cache: AllSongsPaged");
            }

            if (patternCache != null) {
                patternCache.clear();
                log.info("🗑️ Cleared cache: CachedSongsPattern");
            }

            if (fileNamesCache != null) {
                fileNamesCache.clear();
                log.info("🗑️ Cleared cache: CachedFileNames");
            }

            log.info("✅ All song caches evicted successfully");
        } catch (Exception e) {
            log.error("❌ Error evicting caches: {}", e.getMessage(), e);
        }
    }
    protected SongDto updateSongInDb(SongContainer song, String url){
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
        evictAllSongCaches();

        log.info("Saved  Song : {}",savedSong);

        return toDto(savedSong.get());
    }

    @Caching(
            evict = {
                    @CacheEvict( value ="AllSongsPaged", allEntries = true),
                    @CacheEvict(value = "CachedSongsPattern",allEntries = true),
                    @CacheEvict(value = "CachedFileNames",allEntries = true)
            }
    ) // this method is for eviction purpose only.
    public SongDto saveSong(SongContainer song, String url){
        return updateSongInDb(song,url);
    }



    @Cacheable(value = "AllSongsPaged", key = "{#page, #size}")
    public List<SongDto> getAllSongsName(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("songName"));
        return repo.findAll(pageable)
                .getContent()
                .stream()
                .map(this::toDto)
                .toList();
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
            key = "#prefix.toLowerCase().substring(0, #prefix.length() > 2 ? 2 : #prefix.length())"
    )
    public List<SongDto> searchSongsByName(String prefix) {
        log.info("🔍 Database query for prefix: {}", prefix);
        return repo.findBySongNameContainingIgnoreCase(prefix).stream().map(s -> objectMapper.convertValue(s, SongDto.class)).toList();
    }

    @Cacheable(value = "CachedFileNames")
    public List<String> getSongList() {
        log.info("cache hit for songs fileNames");
        return repo.getSongsByFileName();
    }

    public String requestedSongSave(RequestSongDto song){

        RequestSong newSong = objectMapper.convertValue(song, RequestSong.class);
        newSong.setStatus(Status.SENT);

        try{
            songRequestRepo.save(newSong);
        }catch (Exception s){
            log.error("error while saving the requested song {}",s.getMessage());
            return "Failed";
        }
        return "Saved";
    }

    public Optional<List<RequestSongDto>> getAllRequestStatusSong(Status status){

        return Optional.of(songRequestRepo.findRequestSongByStatus(status)
                .map(songs -> songs.stream()
                        .map(song -> objectMapper.convertValue(song, RequestSongDto.class))
                        .toList())
                .orElse(Collections.emptyList()));

    }

    // admin song status update service method
    public String updateStatusForRequestSong(String songName, Status newStatus,String note){
        Optional<RequestSong> currentSong = songRequestRepo.findRequestSongBySongName(songName);

        if(currentSong.isEmpty()){
            return "Song Not found";
        }
        try{
            currentSong.get().setStatus(newStatus);
            currentSong.get().setNote(note);
            songRequestRepo.save(currentSong.get());
        }catch (Exception e){
            log.error("error while updating thr requested song {}",e.getMessage());
            return "Failed";
        }

        return "updated";
    }

    public Optional<List<RequestSongDto>> getAllSongForRequestor(String requestor){
        return Optional.of(repo.findSongsByRequestor(requestor)
                .map(songs -> songs.stream()
                        .map(song -> objectMapper.convertValue(song, RequestSongDto.class))
                        .toList())
                .orElse(Collections.emptyList()));
    }

    public boolean checkSongRequestAvailable(String songName){
        Optional<RequestSong> currentSong = songRequestRepo.findRequestSongBySongName(songName);

        return currentSong.isPresent() && currentSong.get().getStatus().equals(Status.SENT);
    }
}