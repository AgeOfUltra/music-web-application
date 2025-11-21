package com.music.musicwebapplication.controller;

import com.music.musicwebapplication.dto.ConfessContainerRequest;
import com.music.musicwebapplication.entity.User;
import com.music.musicwebapplication.repo.UserRepo;
import com.music.musicwebapplication.service.ConfessService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.validation.Errors;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Optional;

@Slf4j
@Controller
@RequestMapping("/app/music/connect")
public class ConfessController {

    private final ConfessService service;
    private final UserRepo repo;

    public ConfessController(ConfessService service, UserRepo repo) {
        this.service = service;
        this.repo = repo;
    }
    //update db with data.

    @PostMapping("/sendRequest")
    public ModelAndView userRequestData(@Valid @ModelAttribute("requestData") ConfessContainerRequest requestData, Errors error, RedirectAttributes attribute, Authentication auth){
        //validation
       if(error.hasErrors()){
           log.error("Room validation failed due to error : {}", error);
           log.info("failed Data ! : {}", requestData);
           attribute.addFlashAttribute("org.springframework.validation.BindingResult.requestData", error);
           attribute.addFlashAttribute("requestData", requestData);
           return new ModelAndView("redirect:/app/music/dashboard");
       }

        //CONVERT THE DTO TO entity and pass to service for save.
        requestData.setInitiatedBy(auth.getName());

        Optional<User> user = repo.findByUsername(auth.getName());
        user.ifPresent(value -> requestData.setSenderEmail(value.getEmail()));
        log.info("Current user: {} and email {}", auth.getName(), user.get().getEmail());

        String result = service.buildSaveConfessData(requestData);
        if(result.equals("FAILED")){
            attribute.addFlashAttribute("emailStatus", "failed to Send Email");
            return new ModelAndView("redirect:/app/music/dashboard");
        }

        attribute.addFlashAttribute("emailStatus", "Successfully sent your confession!");
        attribute.addFlashAttribute("requestData", requestData);
        return new ModelAndView("redirect:/app/music/dashboard?status=sentSuccess");
    }

}
