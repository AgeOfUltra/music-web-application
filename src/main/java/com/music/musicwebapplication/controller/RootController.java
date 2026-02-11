package com.music.musicwebapplication.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class RootController {

    @GetMapping(value = {"/","/app/music/home"})
    public String redirectToLogin() {
        return "landingpage";
    }

    @GetMapping("/public/internal/speed/test")
    public ResponseEntity<Long> testSPeed(@RequestParam("ts") long timestamp){
        return ResponseEntity.status(HttpStatus.OK).body(timestamp);
    }
}