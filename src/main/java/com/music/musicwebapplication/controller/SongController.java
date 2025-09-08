package com.music.musicwebapplication.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.music.musicwebapplication.dto.SongDto;
import com.music.musicwebapplication.service.SongControllerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.io.InputStream;

@Slf4j
@RestController
@RequestMapping("/api/music")
public class SongController {

    private final SongControllerService songControllerService;
    private final ObjectMapper objectMapper;

    @Autowired
    SongController(SongControllerService songControllerService, ObjectMapper objectMapper){
        this.songControllerService = songControllerService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/upload")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> uploadSong(@RequestParam("file")MultipartFile file,
                                             @RequestParam("song") String  songJson) {

        try {
            if(file.isEmpty()){
                log.info("file is empty");
                return ResponseEntity.badRequest().body("File is empty");
            }
            String contentType = file.getContentType();
            if(contentType==null || !contentType.startsWith("audio/")){
                log.info("incorrect file type");
                return ResponseEntity.badRequest().body("Please upload audio file");
            }
            SongDto song = objectMapper.readValue(songJson, SongDto.class);
            if(song.getSongName().isEmpty() || song.getSongName().isBlank()){
                log.info("song name is required");
                return ResponseEntity.badRequest().body("song name required");
            }

            String result = songControllerService.fileUploadHelper(file,song);
            return ResponseEntity.ok(result);

        }catch (Exception e){
            log.info("Error while uploading file");
            log.error("stack trace : {}", (Object) e.getStackTrace());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to upload song: " + e.getMessage());
        }
    }

    @PreAuthorize("hasAuthority('MUSIC_READ')")
    @GetMapping(value = "/public/streamSong/{name}",produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> streamSong(@PathVariable String name){
        ResponseInputStream<GetObjectResponse> objectStream = songControllerService.getSongStream(name);

        StreamingResponseBody responseBody = outputStream -> {
            try(InputStream inputStream = objectStream){
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer))!= -1){
                    outputStream.write(buffer,0,bytesRead);
                }
            }finally {
                objectStream.close();
            }

        };
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + name + "\"")
                .body(responseBody);
    }
}

/* async version
*
* public CompletableFuture<ResponseEntity<String>> uploadSongAsync(
            @RequestParam("file") MultipartFile file,
            @RequestParam("song") String songJson) {

        try {
            if (file.isEmpty()) {
                return CompletableFuture.completedFuture(
                    ResponseEntity.badRequest().body("File is empty"));
            }

            SongDto song = objectMapper.readValue(songJson, SongDto.class);

            return musicUploadService.fileUploadHelperAsync(file, song)
                    .thenApply(result -> ResponseEntity.ok(result))
                    .exceptionally(throwable -> {
                        logger.error("Async upload failed: {}", throwable.getMessage(), throwable);
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body("Failed to upload song: " + throwable.getMessage());
                    });

        } catch (Exception e) {
            logger.error("Error in async upload: {}", e.getMessage(), e);
            return CompletableFuture.completedFuture(
                ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to upload song: " + e.getMessage()));
        }
    }
    * yml file
    * spring:
  task:
    execution:
      pool:
        core-size: 5
        max-size: 10
        queue-capacity: 25
* */