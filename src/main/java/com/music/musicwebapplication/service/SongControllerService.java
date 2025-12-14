package com.music.musicwebapplication.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.music.musicwebapplication.dto.RequestSongDto;
import com.music.musicwebapplication.dto.SongContainer;
import com.music.musicwebapplication.entity.RequestSong;
import com.music.musicwebapplication.entity.Song;
import com.music.musicwebapplication.repo.SongRepo;
import com.music.musicwebapplication.repo.SongRequestRepo;
import com.music.musicwebapplication.support.Status;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
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
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
public class SongControllerService {

    private final S3Client client;
    private final SongRepo repo;
    private final SongRequestRepo songRequestRepo;
    private final ObjectMapper objectMapper;


    @Value("${aws.bucket.name}")
    private String bucketName;


    @Autowired
    SongControllerService(S3Client client, SongRepo repo, SongRequestRepo songRequestRepo, ObjectMapper objectMapper){
        this.client = client;
        this.repo = repo;
        this.songRequestRepo = songRequestRepo;
        this.objectMapper = objectMapper;
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

            Song uploadedSong = updateSongInDb(container,url);
            log.info("song saved with name {}",uploadedSong.getSongName());

            return "Song uploaded and saved successfully";

        }catch (Exception e){
            log.error("Error during file upload process: {}", e.getMessage(), e);
            throw new Exception("Failed to upload song: " + e.getMessage());
        }
    }
    private String uploadFile(MultipartFile file) throws IOException{
        String s3Key = Objects.requireNonNull(file.getOriginalFilename());
        PutObjectResponse response = client.putObject(PutObjectRequest.builder()
                .bucket(bucketName)
                .key(file.getOriginalFilename())
                .build(), RequestBody.fromBytes(file.getBytes())
        );

        if(response == null || response.eTag()== null){
            throw new IOException("failed to upload the song");
        }
       return s3Key;
    }

    private String getStreamUrl(String fileName){
        return "/app/music/public/streamSong/"+fileName;
    }



    protected Song updateSongInDb(SongContainer song, String url){
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

        return savedSong.get();
    }

    @Cacheable(
            value = "AllSongsPaged",
            key = "{#page, #size}"
    )
    public List<Song> getAllSongsName(int page, int size) {
        Pageable pageable = PageRequest.of(page,size, Sort.by("songName").ascending());
        return  repo.findAll(pageable).getContent();
    }

    @Cacheable(value="CachedSongs",key="#songName")
    public List<Song> searchSongsByName(String songName) {
        log.info("request data : {}" , songName);
        return repo.findBySongNameContainingIgnoreCase(songName);
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

        return songRequestRepo.findRequestSongByStatus(status);

    }

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
}