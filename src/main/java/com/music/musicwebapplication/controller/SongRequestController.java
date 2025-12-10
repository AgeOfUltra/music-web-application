package com.music.musicwebapplication.controller;

import com.music.musicwebapplication.dto.RequestSongDto;
import com.music.musicwebapplication.service.SongControllerService;
import com.music.musicwebapplication.support.Status;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.validation.Errors;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/app/music/public")
@Slf4j
public class SongRequestController {

    private final SongControllerService songService;

    public SongRequestController(SongControllerService songService) {
        this.songService = songService;
    }

    @PostMapping("/request/song")
    public ModelAndView submitSongRequest(@Valid @ModelAttribute("requestSong") RequestSongDto requestSongDto, Errors error, RedirectAttributes redirectAttributes){
        if(error.hasErrors()){
            log.error("invalid data requested {}", requestSongDto);
            redirectAttributes.addFlashAttribute("org.springframework.validation.BindingResult.requestSong", error);
            redirectAttributes.addFlashAttribute("requestSong", requestSongDto);
            return new ModelAndView("redirect:/app/music/dashboard");
        }
        String result = songService.requestedSongSave(requestSongDto);
        if(result.contains("Failed")){
            redirectAttributes.addFlashAttribute("requestSongError", "Request Sent failed. Please try again");
            return new ModelAndView("redirect:/app/music/dashboard?status=requestFailed");
        }

        log.info("requested Data {}", requestSongDto);
        redirectAttributes.addFlashAttribute("requestSongError", "Request Sent Success full");
        return new ModelAndView("redirect:/app/music/dashboard?status=requestSent");
    }


}
