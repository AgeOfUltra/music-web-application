package com.music.musicwebapplication.controller;

import com.music.musicwebapplication.dto.SongDto;
import com.music.musicwebapplication.exception.SongNotFoundException;
import com.music.musicwebapplication.repo.SongRepo;
import com.music.musicwebapplication.service.SongCacheService;
import com.music.musicwebapplication.service.AudioStreamService;
import com.music.musicwebapplication.utils.JwtTokenUtil;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
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
import org.springframework.web.context.request.async.DeferredResult;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.List;


@Controller
@RequestMapping("/app/music/audio")
@Slf4j
public class AudioStreamController {

    private final AudioStreamService audioStreamService;
    private final SongCacheService songCacheService;
    private final SongRepo repo;
    private final JwtTokenUtil tokenUtil;


    @Autowired
    AudioStreamController(AudioStreamService audioStreamService, SongCacheService songCacheService, SongRepo repo, JwtTokenUtil tokenUtil){
        this.audioStreamService = audioStreamService;
        this.songCacheService = songCacheService;
        this.repo = repo;
        this.tokenUtil = tokenUtil;
    }

    @GetMapping(value = "/public/streamSong/{currentUsername}/{name}", produces = "audio/mpeg")
    public DeferredResult<ResponseEntity<Resource>> streamSong(@PathVariable String currentUsername, @PathVariable String name, @RequestParam("token") String token, @RequestParam("ts") long timestamp) {

        long now = System.currentTimeMillis();

        if (Math.abs(now - timestamp) > 75_000) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Expired stream request"
            );
        }


        try {
            String username = tokenUtil.getIdentityFromToken(token);

            if (username == null || !username.equals(currentUsername)) {
                throw new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Expired stream request"
                );
            }

        } catch (ExpiredJwtException e) {
            log.warn("Expired token: {}", e.getMessage());
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Expired stream request"
            );

        } catch (JwtException | IllegalArgumentException e) {
            log.error("Invalid token: {}", e.getMessage());
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Expired stream request"
            );
        }


        DeferredResult<ResponseEntity<Resource>> deferredResult = new DeferredResult<>(30000L); // 30 sec timeout

        // Handle timeout
        deferredResult.onTimeout(() -> {
            log.warn("⏱️ Request timeout for song: {}", name);
            deferredResult.setErrorResult(
                    ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT).build()
            );
        });

        // Handle the async response
        songCacheService.getCachedResource(name)
                .thenAccept(resource -> {
                    try {
                        ResponseEntity<Resource> response = ResponseEntity.ok()
                                .contentType(MediaType.parseMediaType("audio/mpeg"))
                                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + name + "\"")
                                .contentLength(resource.contentLength())
                                .body(resource);

                        deferredResult.setResult(response);

                    } catch (IOException e) {
                        log.error("❌ Error reading resource length for {}: {}", name, e.getMessage());
                        deferredResult.setErrorResult(
                                ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
                        );
                    }
                })
                .exceptionally(ex -> {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;

                    if (cause instanceof SongNotFoundException) {
                        log.warn("⚠️ Song not found: {}", name);
                        deferredResult.setErrorResult(ResponseEntity.notFound().build());
                    } else if (cause instanceof IOException) {
                        log.error("❌ IO Error streaming song {}: {}", name, cause.getMessage());
                        deferredResult.setErrorResult(
                                ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
                        );
                    } else {
                        log.error("❌ Unexpected error streaming song {}: {}", name, cause.getMessage());
                        deferredResult.setErrorResult(
                                ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
                        );
                    }

                    return null;
                });

        return deferredResult;
    }

    @GetMapping("/fetchAllSongs")
    public ResponseEntity<Page<SongDto>> getSongsAsRequired(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        List<SongDto> content =
                audioStreamService.getAllSongsName(page, size);

        long total = repo.count();

        return ResponseEntity.ok(
                new PageImpl<>(content, PageRequest.of(page, size), total)
        );
    }

    @GetMapping("/searchSong")
    public ResponseEntity<List<SongDto>> searchSongsByName(@RequestParam String query){
        return ResponseEntity.ok(audioStreamService.searchSongsByName(query));
    }

    @GetMapping("/getAllCachedSongs")
    public ResponseEntity<List<String>> getAllCachedSongs(){
        return ResponseEntity.ok(audioStreamService.getSongList());
    }

}
