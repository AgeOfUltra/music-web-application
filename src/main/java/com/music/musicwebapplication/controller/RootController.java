package com.music.musicwebapplication.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class RootController {

    @GetMapping("/")
    public String redirectToLogin() {
        return "redirect:/app/music/public/login";
    }
}