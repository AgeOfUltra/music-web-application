package com.music.musicwebapplication.controller;

import com.music.musicwebapplication.dto.ConfessDto;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.validation.Errors;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Slf4j
@Controller
@RequestMapping("/app/music/connect")
public class ConfessController {
    //update db with data.

    @PostMapping("/sendRequest")
    public ModelAndView userRequestData(@Valid @ModelAttribute("requestData") ConfessDto requestData, Errors error, RedirectAttributes attribute){
        //validation
       if(error.hasErrors()){
           log.error("Room validation failed due to error : {}", error);
           log.info("failed Data ! : {}", requestData);
           attribute.addFlashAttribute("org.springframework.validation.BindingResult.newRoom", error);
           attribute.addFlashAttribute("requestData", requestData);
           attribute.addFlashAttribute("emailStatus", "failed to Send Email");
           return new ModelAndView("redirect:/app/music/dashboard");
       }

        //CONVERT THE DTO TO entity and pass to service.





        attribute.addFlashAttribute("emailStatus", "SuccessFully Sent Email");
        return new ModelAndView("redirect:/app/music/dashboard?status=sentSuccess");
    }


}
