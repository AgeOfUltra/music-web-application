package com.music.musicwebapplication.controller;

import com.music.musicwebapplication.dto.GuestLogin;
import com.music.musicwebapplication.exception.ConfessRoomException;
import com.music.musicwebapplication.service.ConfessService;
import org.modelmapper.internal.bytebuddy.implementation.bytecode.Throw;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

@Controller()
@RequestMapping("/app/music/node")
public class ConfessPublicJoin {
    private final ConfessService service ;

    public ConfessPublicJoin(ConfessService service) {
        this.service = service;
    }


//    this is external link url : /app/music/node/join?sender=jarvis&roomId=abcdef
    @GetMapping("/join")
    public String displayJoinRoom(@RequestParam(required = false) String sender, @RequestParam(required = false) String roomId,@RequestParam(required = false) String error, Model model){

        if(sender.isBlank() || roomId.isBlank()){
            throw new ConfessRoomException("Oops! invalid request");
        }

        boolean isValidRoomHash = service.validateRoomHash(roomId,sender);

       if(isValidRoomHash){
           model.addAttribute("sender",sender);
           model.addAttribute("roomId",roomId);
           model.addAttribute("roomLogin",new GuestLogin());
           return "passcode";
       }else{
           throw new ConfessRoomException("Oops your room not found");
       }

    }

//   redirected to

}
