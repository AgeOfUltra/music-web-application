package com.music.musicwebapplication.controller;

import com.music.musicwebapplication.dto.RequestSongDto;
import com.music.musicwebapplication.dto.SongDto;
import com.music.musicwebapplication.service.PublicAuthService;
import com.music.musicwebapplication.service.AudioStreamService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.propertyeditors.StringTrimmerEditor;
import org.springframework.stereotype.Controller;
import org.springframework.validation.Errors;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/app/music")
@Slf4j
public class SongRequestController {

    private final AudioStreamService songService;
    private final PublicAuthService userService;

    public SongRequestController(AudioStreamService songService, PublicAuthService userService) {
        this.songService = songService;
        this.userService = userService;
    }

    @InitBinder
    public void initBinder(WebDataBinder binder) {
        binder.registerCustomEditor(
                String.class,
                new StringTrimmerEditor(true) // trims + converts "" to null
        );
    }

    @PostMapping("/request/song")
    public ModelAndView submitSongRequest(@Valid @ModelAttribute("requestSong") RequestSongDto requestSongDto, Errors error, RedirectAttributes redirectAttributes){
        if(error.hasErrors()){
            log.error("invalid data requested {}", requestSongDto);
            redirectAttributes.addFlashAttribute("org.springframework.validation.BindingResult.requestSong", error);
            redirectAttributes.addFlashAttribute("requestSong", requestSongDto);
            return new ModelAndView("redirect:/app/music/dashboard");
        }

        //        check whether the song already exist.
        List<SongDto> checkSong = songService.searchSongsByName(requestSongDto.getSongName());
        if(!checkSong.isEmpty()){
            redirectAttributes.addFlashAttribute("requestSongError", "Song Already exist in globally");
            return new ModelAndView("redirect:/app/music/dashboard");
        }

        if(songService.checkSongRequestAvailable(requestSongDto.getSongName())){
            redirectAttributes.addFlashAttribute("requestSongError", "Song Already requested By Other user");
            return new ModelAndView("redirect:/app/music/dashboard");
        }


        requestSongDto.setEmail(userService.getUserEmail(requestSongDto.getRequestor()));
        String result = songService.requestedSongSave(requestSongDto);
        if(result.contains("Failed")){
            redirectAttributes.addFlashAttribute("requestSongError", "Request Sent failed. Please try again");
            return new ModelAndView("redirect:/app/music/dashboard?status=requestFailed");
        }

        log.info("requested Data {}", requestSongDto);
        redirectAttributes.addFlashAttribute("requestSongError", "Request Sent Success full");
        return new ModelAndView("redirect:/app/music/dashboard?status=requestSent");
    }

    @PostMapping("/request/update")
    public ModelAndView adminUpdateActivity(RequestSongDto requestSongDto,RedirectAttributes model){
        String result = songService.updateStatusForRequestSong(requestSongDto.getSongName(), requestSongDto.getStatus(), requestSongDto.getNote());

        if(result.equals("Song Not found") || result.equals("Failed")){
            model.addFlashAttribute("errorUpdate","Error Song not found, check Manually!");
        }else{
            model.addFlashAttribute("successUpdate","Updated!");
        }

        return new ModelAndView("redirect:/app/music/connect/request/admin/inProgress");
    }



}
