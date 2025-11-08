package com.music.musicwebapplication.controller;

import com.music.musicwebapplication.dto.CreateRoom;
import com.music.musicwebapplication.dto.JoinRoom;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/app/music")
public class DashboardController {

    @GetMapping("/dashboard")
    public String dashboardPage(Model model, Authentication authentication) {
        model.addAttribute("currentUser",authentication.getName());
        if(!model.containsAttribute("newRoom")){
            model.addAttribute("newRoom",new CreateRoom());
        }

        if(!model.containsAttribute("joinRoom")){
            model.addAttribute("joinRoom",new JoinRoom());
        }
        return "dashboard";
    }
}