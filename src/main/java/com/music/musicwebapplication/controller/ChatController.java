package com.music.musicwebapplication.controller;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/app/music")
public class ChatController {

    @GetMapping("/chat")
    public String chatRoom(@RequestParam(required = false) String roomId,
                           Authentication authentication,
                           Model model) {
        // Add user information to the model
        model.addAttribute("username", authentication.getName());
        model.addAttribute("roomId", roomId != null ? roomId : "general");

        // This will return the chat.html template
        return "chat";
    }
}