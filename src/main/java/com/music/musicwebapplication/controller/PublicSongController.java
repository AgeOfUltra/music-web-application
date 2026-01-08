package com.music.musicwebapplication.controller;

import com.music.musicwebapplication.dto.SongDto;
import com.music.musicwebapplication.exception.SongNotFoundException;
import com.music.musicwebapplication.repo.SongRepo;
import com.music.musicwebapplication.service.SongCacheService;
import com.music.musicwebapplication.service.SongControllerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
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
    public ResponseEntity<Resource> streamSong(@PathVariable String name) {
        try {
            Resource resource = songCacheService.getCachedResource(name);

            // Handle client disconnection
            if (resource == null) {
                log.debug("⚠️ Resource unavailable (client disconnected): {}", name);
                return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("audio/mpeg"))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + name + "\"")
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .body(resource);

        } catch (SongNotFoundException e) {
            log.error("❌ Song not found: {}", name);
            return ResponseEntity.notFound().build();

        } catch (IOException e) {
            log.error("❌ Error streaming song {}: {}", name, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
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
        return ResponseEntity.ok(songControllerService.getSongList());
    }

}
