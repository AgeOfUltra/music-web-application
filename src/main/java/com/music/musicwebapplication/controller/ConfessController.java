package com.music.musicwebapplication.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.music.musicwebapplication.dto.ConfessContainerRequest;
import com.music.musicwebapplication.dto.RequestSongDto;
import com.music.musicwebapplication.entity.Confess;
import com.music.musicwebapplication.entity.User;
import com.music.musicwebapplication.repo.UserRepo;
import com.music.musicwebapplication.service.ConfessService;
import com.music.musicwebapplication.service.SongControllerService;
import com.music.musicwebapplication.support.Status;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.Errors;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;


@Slf4j
@Controller
@RequestMapping("/app/music/connect")
public class ConfessController {

    private final ConfessService service;
    private final UserRepo repo;
    private final ObjectMapper objectMapper;

    private final SongControllerService songService;
    public ConfessController(ConfessService service, UserRepo repo, ObjectMapper objectMapper, SongControllerService songService) {
        this.service = service;
        this.repo = repo;
        this.objectMapper = objectMapper;
        this.songService = songService;
    }

//    admin validation page controller

//    url : /app/music/connect/request/inProgress
    @GetMapping("/request/inProgress/{user}")
    public String getAllInProgressRequest(@PathVariable(value = "user")String currentUser, Model model){
        Optional<List<Confess>> availableRequest;
        Optional<List<RequestSongDto>> requestedSongs;

        if(currentUser.equals("admin")){
            availableRequest = service.getAllInProgressRequest(Status.IN_PROGRESS);
            requestedSongs= songService.getAllRequestStatusSong(Status.SENT);
        }else{
            availableRequest=service.getAllRequestForUser(currentUser);
            requestedSongs = songService.getAllSongForRequestor(currentUser);
        }

        if(availableRequest.isEmpty() || requestedSongs.isEmpty()){
            model.addAttribute("noData","No Pending request");
            model.addAttribute("noRequestData","No Pending request");
        }else{
            List<ConfessContainerRequest> confessDto = availableRequest.get().stream().map(entity -> objectMapper.convertValue(entity, ConfessContainerRequest.class))
                    .collect(Collectors.toList());
            model.addAttribute("inProgressRequests",confessDto);
            model.addAttribute("pendingOrCompleted",requestedSongs.get());
        }

        return "adminValidation";
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

        attribute.addFlashAttribute("emailStatus", "Confess sent for Validation!");
        attribute.addFlashAttribute("requestData", requestData);
        return new ModelAndView("redirect:/app/music/dashboard?status=sentSuccess");
    }

//   TODO :  admin dashBoard error object need to be configured

//    url : /app/music/connect/changeStatus/{roomId}/{s2}
    @PostMapping("/changeStatus/{roomId}/{s2}")
    public ModelAndView updateRequestStatus(@PathVariable String roomId, @PathVariable String s2 , RedirectAttributes model){
        log.info("initiating the status update service process ,Request : {},{}",s2,roomId);
        Status newStatus = s2.equals("APPROVED") ? Status.APPROVED : Status.REJECTED;
        Map<String,String> response  = service.updateStatus(Status.IN_PROGRESS,newStatus,roomId);
        if(response.containsKey("error")){
            model.addFlashAttribute("errorUpdate","update failed");

        }else{
            model.addFlashAttribute("successUpdate",response.get("saved"));
        }

        log.info("Finished the updating process : {},{}",s2,roomId);
//        FIXME: redirect to the same page.

         return new ModelAndView("redirect:/app/music/connect/request/inProgress/admin");
    }

}
