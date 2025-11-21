package com.music.musicwebapplication.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

@Controller()
@RequestMapping("/app/music/node")
public class ConfessPublicJoin {

    @GetMapping("/join")
    public void displayJoinRoom(@RequestParam("roomId") String roomId){

    }

}
