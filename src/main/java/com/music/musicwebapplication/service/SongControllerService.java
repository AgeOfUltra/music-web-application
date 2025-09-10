package com.music.musicwebapplication.service;

import com.music.musicwebapplication.dto.SongDto;
import com.music.musicwebapplication.entity.Song;
import com.music.musicwebapplication.exception.SongNotFoundException;
import com.music.musicwebapplication.repo.SongRepo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import javax.swing.text.html.Option;
import java.io.IOException;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Service
public class SongControllerService {

    private final S3Client client;
    private final SongRepo repo;

    @Value("${aws.bucket.name}")
    private String bucketName;

//    @Value("${cloud.aws.region.static}")
//    private String region;

    @Autowired
    SongControllerService(S3Client client, SongRepo repo){
        this.client = client;
        this.repo = repo;
    }

    public String fileUploadHelper(MultipartFile file,
                                   SongDto song) throws Exception {

        log.info("Song uploading process started");

        String savedResult= "";
        Optional<Song> currentSong = repo.findSongBySongName(song.getSongName());

        if(currentSong.isPresent()){
            log.info("Song uploading failed! because song already exist in data base with id {}",currentSong.get().getId());
            return "Song already exist";
        }

        try{
            String s3key = uploadFile(file);
            log.info("Song uploaded successfully with key {}",s3key);

//            String url = generateUrl(parseString(song.getFileName()));
            String url = getS3Url(s3key);
            log.info("Generated URL: {}", url);

            Song uploadedSong = updateSongInDb(song,url);
            log.info("song saved with name {}",uploadedSong.getSongName());

            return "Song uploaded and saved successfully";

        }catch (Exception e){
            log.error("Error during file upload process: {}", e.getMessage(), e);
            throw new Exception("Failed to upload song: " + e.getMessage());
        }
    }
    private String uploadFile(MultipartFile file) throws IOException{
        String s3Key = parseString(Objects.requireNonNull(file.getOriginalFilename()));
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

    private String getS3Url(String s3Key) {
        return client.utilities().getUrl(builder -> builder
                .bucket(bucketName).
                key(s3Key)).
                toExternalForm();
    }
//
//    private String generateUrl(String fileName){
//        return  String.format("https://%s.s3.%s.amazonaws.com/%s",
//                bucketName, region, fileName);
//    }

    private String parseString(String fileName){
        return  fileName.replace(" ","+");
    }

    @Transactional
    protected Song updateSongInDb(SongDto song, String url){
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

    public ResponseInputStream<GetObjectResponse> getSongStream(String objectKey){

        Optional<Song> song = repo.findSongByFileName(objectKey);
        if(song.isEmpty()){
            log.info("Song not found in DB with name {}",objectKey);
           throw new SongNotFoundException("Song not found");
        }
        //object key is same as file name
        GetObjectRequest  getObjectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(objectKey)
                .build();
        return client.getObject(getObjectRequest);

    }

}


/*
*  async version
*  public CompletableFuture<String> fileUploadHelperAsync(MultipartFile file, SongDto song) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return fileUploadHelper(file, song);
            } catch (Exception e) {
                logger.error("Async upload failed: {}", e.getMessage(), e);
                throw new RuntimeException(e);
            }
        }, executorService);
    }
*public void shutdown() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
    }
* */