package com.music.musicwebapplication.controller;

import com.music.musicwebapplication.dto.SongContainer;
import com.music.musicwebapplication.service.SongControllerService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.Errors;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
@Controller
@Slf4j
@RequestMapping("/app/music/admin")
public class AdminSongController {

    @GetMapping("/songUpload")
    @PreAuthorize("isAuthenticated()")
    public String uploadSongPage(Model model) {
        if (!model.containsAttribute("songContainer")) {
            model.addAttribute("songContainer", new SongContainer());
        }
        return "upload";
    }

    private final SongControllerService songControllerService;

    @Autowired
    AdminSongController(SongControllerService songControllerService) {
        this.songControllerService = songControllerService;
    }

    @PostMapping("/upload")
    @PreAuthorize("isAuthenticated()")
    public ModelAndView uploadSong(@Valid @ModelAttribute("songContainer") SongContainer songContainer, Errors error, RedirectAttributes redirectAttributes) {
        if (error.hasErrors()) {
            log.error("Validation failed for song container, {}", songContainer);
            redirectAttributes.addFlashAttribute("org.springframework.validation.BindingResult.songContainer", error);
            redirectAttributes.addFlashAttribute("songContainer", songContainer);
            redirectAttributes.addFlashAttribute("songUploadFailed", "Please provide valid fields");
            return new ModelAndView("redirect:/app/music/admin/songUpload");
        }

        songContainer.setMovie(songContainer.getMovie().replace("'","\\'"));
        songContainer.setSinger(songContainer.getSinger().replace("'","\\'"));
        ResponseEntity<?> response;
        try {
            response = uploadSongApi(songContainer);
            if (response.getStatusCode().equals(HttpStatus.OK)) {
                log.info(
                        "Song Uploaded successFull {} ,{}", songContainer.getSongName(), songContainer.getFileName()
                );
                redirectAttributes.addFlashAttribute("songUploadedSuccess", true);

            } else {
                log.error("error occurred while upload song");
                redirectAttributes.addFlashAttribute("songUploadFailed", response.getBody());
                redirectAttributes.addFlashAttribute("songContainer", songContainer);
            }
            return new ModelAndView("redirect:/app/music/admin/songUpload");
        } catch (Exception e) {
            log.info("Error while uploading file");
            log.error("stack trace : {}", (Object) e.getStackTrace());
            redirectAttributes.addFlashAttribute("songUploadFailed", e.getMessage());
            return new ModelAndView("redirect:/app/music/admin/songUpload");
        }
    }

    //
    public ResponseEntity<String> uploadSongApi(SongContainer container) throws Exception {

        MultipartFile file = container.getFile();

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("audio/")) {
            log.info("incorrect file type");
            return ResponseEntity.badRequest().body("Please upload audio file");
        }

        if (!container.getFileName().contains(".mp3")) {
            log.error("Sent file name {}", container.getFileName());
            return ResponseEntity.badRequest().body("Only mp3 files are accepted");
        }

        String result = songControllerService.fileUploadHelper(container);
        return ResponseEntity.ok(result);

    }


}
