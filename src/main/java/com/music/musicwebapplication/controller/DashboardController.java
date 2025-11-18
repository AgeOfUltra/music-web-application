package com.music.musicwebapplication.controller;

import com.music.musicwebapplication.dto.CreateRoom;
import com.music.musicwebapplication.dto.JoinRoom;
import com.music.musicwebapplication.dto.ConfessContainerRequest;
import com.music.musicwebapplication.entity.User;
import com.music.musicwebapplication.repo.UserRepo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Optional;

@Slf4j
@Controller
@RequestMapping("/app/music")
public class DashboardController {

    private final UserRepo repo;

    public DashboardController(UserRepo repo) {
        this.repo = repo;
    }

    @GetMapping("/dashboard")
    public String dashboardPage(Model model, Authentication authentication) {
        String currentUser = authentication.getName();

        Optional<User> user = repo.findByUsername(currentUser);

        model.addAttribute("currentUser",currentUser);
        user.ifPresent(value -> model.addAttribute("currentUserEmail", value.getEmail()));

        if(!model.containsAttribute("newRoom")){
            model.addAttribute("newRoom",new CreateRoom());
        }

        if(!model.containsAttribute("requestData")){
            model.addAttribute("requestData",new ConfessContainerRequest());
        }
        if(!model.containsAttribute("joinRoom")){
            model.addAttribute("joinRoom",new JoinRoom());
        }
        return "dashboard";
    }
}