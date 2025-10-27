package com.music.musicwebapplication.controller;

import com.music.musicwebapplication.entity.Song;
import com.music.musicwebapplication.service.SongControllerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.io.InputStream;
import java.util.List;


@Controller
@RequestMapping("/app/music")
@Slf4j
public class PublicSongController {

    private final SongControllerService songControllerService;

    @Autowired
    PublicSongController(SongControllerService songControllerService){
        this.songControllerService = songControllerService;
    }
//    @PreAuthorize("hasAuthority('MUSIC_READ')")
    @GetMapping(value = "/public/streamSong/{name}",produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> streamSong(@PathVariable String name){
        log.info("Initiated the song Stream Request for file name : {}",name);
        ResponseInputStream<GetObjectResponse> objectStream = songControllerService.getSongStream(name);

        StreamingResponseBody responseBody = outputStream -> {
            try(InputStream inputStream = objectStream){
                log.info("Streaming started ..");
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

    @GetMapping("/fetchAllSongs")
   public ResponseEntity<Page<Song>> getSongsAsRequired(
           @RequestParam (defaultValue = "0") int page,
           @RequestParam(defaultValue = "20")int size){

        return ResponseEntity.ok(songControllerService.getAllSongsName(page, size));

    }

    protected Page<Song> getAllSongs(){
        return songControllerService.getAllSongsName(0,10);
    }

    @GetMapping("/searchSong")
    public ResponseEntity<List<Song>> searchSongsByName(@RequestParam String query){
        return ResponseEntity.ok(songControllerService.searchSongsByName(query));
    }

}
