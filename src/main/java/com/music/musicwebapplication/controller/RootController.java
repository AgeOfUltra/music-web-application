package com.music.musicwebapplication.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class RootController {

    @GetMapping(value = {"/","/app/music/home"})
    public String redirectToLogin() {
        return "landingpage";
    }
}