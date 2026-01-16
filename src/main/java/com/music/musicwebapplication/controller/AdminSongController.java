package com.music.musicwebapplication.controller;

import com.music.musicwebapplication.dto.SongUploadContainer;
import com.music.musicwebapplication.service.AudioStreamService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.propertyeditors.StringTrimmerEditor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.Errors;
import org.springframework.web.bind.WebDataBinder;
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
            model.addAttribute("songContainer", new SongUploadContainer());
        }
        return "upload";
    }

    private final AudioStreamService audioStreamService;

    @Autowired
    AdminSongController(AudioStreamService audioStreamService) {
        this.audioStreamService = audioStreamService;
    }

    @InitBinder
    public void initBinder(WebDataBinder binder) {
        binder.registerCustomEditor(
                String.class,
                new StringTrimmerEditor(true) // trims + converts "" to null
        );
    }


    @PostMapping(
            value = "/upload",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    @PreAuthorize("isAuthenticated()")
    public ModelAndView uploadSong(
            @Valid @ModelAttribute("songContainer") SongUploadContainer songContainer,
            Errors errors,
            RedirectAttributes redirectAttributes) {

        // ---------------- Validation errors ----------------
        if (errors.hasErrors()) {
            redirectAttributes.addFlashAttribute(
                    "org.springframework.validation.BindingResult.songContainer", errors);
            redirectAttributes.addFlashAttribute("songContainer", songContainer);
            redirectAttributes.addFlashAttribute("songUploadFailed", "Invalid input data");
            return new ModelAndView("redirect:/app/music/admin/songUpload");
        }

        MultipartFile file = songContainer.getFile();

        // ---------------- File presence ----------------
        if (file == null || file.isEmpty()) {
            redirectAttributes.addFlashAttribute("songUploadFailed", "Audio file is required");
            return new ModelAndView("redirect:/app/music/admin/songUpload");
        }

        // ---------------- File size limit ----------------
        long maxSize = 50L * 1024 * 1024; // 50 MB
        if (file.getSize() > maxSize) {
            redirectAttributes.addFlashAttribute(
                    "songUploadFailed", "File size exceeds 50 MB limit");
            return new ModelAndView("redirect:/app/music/admin/songUpload");
        }

        // ---------------- Filename validation ----------------
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.toLowerCase().endsWith(".mp3")) {
            redirectAttributes.addFlashAttribute(
                    "songUploadFailed", "Only MP3 audio files are allowed");
            return new ModelAndView("redirect:/app/music/admin/songUpload");
        }

        // Prevent path traversal / weird filenames
        if (!originalFilename.matches("[a-zA-Z0-9._\\- ]+\\.mp3")) {
            redirectAttributes.addFlashAttribute(
                    "songUploadFailed", "Invalid filename");
            return new ModelAndView("redirect:/app/music/admin/songUpload");
        }

        // ---------------- Content-Type sanity check ----------------
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("audio/")) {
            redirectAttributes.addFlashAttribute(
                    "songUploadFailed", "Invalid audio content type");
            return new ModelAndView("redirect:/app/music/admin/songUpload");
        }

        // ---------------- 🔐 SECURITY SCAN HOOK ----------------
        // Hook point for ClamAV / VirusTotal / custom scanner
        try {
            runSecurityScan(file);
        } catch (SecurityException se) {
            log.warn("🚨 Security scan failed for file {}", originalFilename);
            redirectAttributes.addFlashAttribute(
                    "songUploadFailed", "File failed security scan");
            return new ModelAndView("redirect:/app/music/admin/songUpload");
        } catch (Exception e) {
            log.error("❌ Security scan error", e);
            redirectAttributes.addFlashAttribute(
                    "songUploadFailed", "Unable to scan file. Try again later");
            return new ModelAndView("redirect:/app/music/admin/songUpload");
        }

        // ---------------- Normalize metadata ----------------
        songContainer.setFileName(originalFilename);
        songContainer.setSongName(songContainer.getSongName().replace(".mp3",""));

        // ---------------- Upload ----------------
        try {
            audioStreamService.fileUploadHelper(songContainer);
            redirectAttributes.addFlashAttribute("songUploadedSuccess", true);
        } catch (Exception e) {
            log.error("❌ Song upload failed", e);
            redirectAttributes.addFlashAttribute(
                    "songUploadFailed", e.getMessage());
        }

        return new ModelAndView("redirect:/app/music/admin/songUpload");
    }

    private void runSecurityScan(MultipartFile file) throws Exception {

        // Example 1: Placeholder (always passes)
        // Replace with real implementation
        // ----------------------------------
        // if (virusDetected) {
        //     throw new SecurityException("Virus detected");
        // }

        // Example 2: File signature sanity check
        byte[] header = file.getBytes();
        if (header.length < 3 || header[0] != 'I' || header[1] != 'D' || header[2] != '3') {
            log.warn("⚠️ MP3 header missing ID3 tag");
            // Not all MP3s have ID3, so log only
        }

        // Example 3: External scanner (pseudo-code)
        // virusScanner.scan(file.getInputStream());
    }

}
