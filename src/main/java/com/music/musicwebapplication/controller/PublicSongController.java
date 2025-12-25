package com.music.musicwebapplication.controller;

import com.music.musicwebapplication.dto.SongDto;
import com.music.musicwebapplication.entity.Song;
import com.music.musicwebapplication.repo.SongRepo;
import com.music.musicwebapplication.service.SongCacheService;
import com.music.musicwebapplication.service.SongControllerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
import java.util.List;


@Controller
@RequestMapping("/app/music/audio")
@Slf4j
public class PublicSongController {

    private final SongControllerService songControllerService;
    private final SongCacheService songCacheService;
    private final SongRepo repo;


    @Autowired
    PublicSongController(SongControllerService songControllerService, SongCacheService songCacheService, SongRepo repo){
        this.songControllerService = songControllerService;
        this.songCacheService = songCacheService;
        this.repo = repo;
    }

    @GetMapping(value = "/public/streamSong/{name}",produces = "audio/mpeg")
    public ResponseEntity<Resource> streamSong(@PathVariable String name) throws IOException {
        log.info("Requested song name / file name {}",name);
        Resource songResource = songCacheService.getCachedResource(name);

        return ResponseEntity.ok()
                .contentType(MediaType.valueOf("audio/mpeg"))
                .body(songResource);
    }

    @GetMapping("/fetchAllSongs")
    public ResponseEntity<Page<SongDto>> getSongsAsRequired(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        List<SongDto> content =
                songControllerService.getAllSongsName(page, size);

        long total = repo.count();

        return ResponseEntity.ok(
                new PageImpl<>(content, PageRequest.of(page, size), total)
        );
    }



    @GetMapping("/searchSong")
    public ResponseEntity<List<SongDto>> searchSongsByName(@RequestParam String query){
        return ResponseEntity.ok(songControllerService.searchSongsByName(query));
    }

    @GetMapping("/getAllCachedSongs")
    public ResponseEntity<List<String>> getAllCachedSongs(){
        return ResponseEntity.ok(songCacheService.getSongList());
    }

}
