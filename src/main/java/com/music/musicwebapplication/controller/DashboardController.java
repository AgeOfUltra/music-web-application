package com.music.musicwebapplication.controller;

import com.music.musicwebapplication.dto.CreateRoom;
import com.music.musicwebapplication.dto.ConfessDto;
import com.music.musicwebapplication.dto.RequestSongDto;
import com.music.musicwebapplication.dto.RoomJoin;
import com.music.musicwebapplication.entity.User;
import com.music.musicwebapplication.enums.Status;
import com.music.musicwebapplication.repo.UserRepo;
import com.music.musicwebapplication.service.ConfessService;
import com.music.musicwebapplication.service.AudioStreamService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.propertyeditors.StringTrimmerEditor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.Errors;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;
import java.util.Optional;


@Slf4j
@Controller
@RequestMapping("/app/music")
public class DashboardController {

    private final ConfessService service;
    private final UserRepo repo;

    private final AudioStreamService songService;

    public DashboardController(ConfessService service, UserRepo repo, AudioStreamService songService) {
        this.service = service;
        this.repo = repo;
        this.songService = songService;
        log.debug("DashboardController initialized");
    }


    @GetMapping("/dashboard")
    public String dashboardPage(Model model, Authentication authentication) {
        String currentUser = authentication.getName();
        log.debug("Dashboard page accessed by user: {}", currentUser);

        model.addAttribute("currentUser",currentUser);
        model.addAttribute("alreadySentRequest",service.isRequestCreatedInLast24hours(currentUser));

        if(!model.containsAttribute("newRoom")){
            model.addAttribute("newRoom",new CreateRoom());
        }

        if(!model.containsAttribute("requestData")){
            model.addAttribute("requestData",new ConfessDto());
        }

        if(!model.containsAttribute("requestSong")){
            model.addAttribute("requestSong",new RequestSongDto());
        }
        if(!model.containsAttribute("joinRoom")){
            model.addAttribute("joinRoom",new RoomJoin());
        }
        log.debug("Dashboard model attributes prepared for user: {}", currentUser);
        return "dashboard";
    }
    @InitBinder
    public void initBinder(WebDataBinder binder) {
        binder.registerCustomEditor(
                String.class,
                new StringTrimmerEditor(true) // trims + converts "" to null
        );
    }

    //    url : /app/music/connect/request/inProgress
    @GetMapping("/connect/request/{user}/inProgress")
    public String getAllInProgressRequest(@PathVariable(value = "user")String currentUser, Model model){
        log.info("Fetching in-progress requests for user: {}", currentUser);
        Optional<List<ConfessDto>> availableRequest;
        Optional<List<RequestSongDto>> requestedSongs;

        if(currentUser.equals("admin")){
            log.debug("Admin user detected, fetching all in-progress requests");
            availableRequest = service.getAllInProgressRequest(Status.IN_PROGRESS);
            requestedSongs= songService.getAllRequestStatusSong(Status.SENT);
        }else{
            log.debug("Regular user detected, fetching user-specific requests");
            availableRequest=service.getAllRequestForUser(currentUser);
            requestedSongs = songService.getAllSongForRequestor(currentUser);
        }

        if(availableRequest.isEmpty() || requestedSongs.isEmpty()){
            log.debug("No pending requests found for user: {}", currentUser);
            model.addAttribute("noData","No Pending request");
            model.addAttribute("noRequestData","No Pending request");
        }else{
            log.debug("Found {} confess requests and {} song requests for user: {}",
                    availableRequest.get().size(), requestedSongs.get().size(), currentUser);
            model.addAttribute("inProgressRequests",availableRequest.get());
            model.addAttribute("pendingOrCompleted",requestedSongs.get());
        }

        return "validation";
    }

    @PostMapping("/connect/sendRequest")
    public ModelAndView userRequestData(@Valid @ModelAttribute("requestData") ConfessDto requestData, Errors error, RedirectAttributes attribute, Authentication auth){
        log.info("Received confess request from user: {}", auth.getName());
        //validation
        if(error.hasErrors()){
            log.error("Confess request validation failed for user {}: {}", auth.getName(), error);
            log.debug("Failed request data: {}", requestData);
            attribute.addFlashAttribute("org.springframework.validation.BindingResult.requestData", error);
            attribute.addFlashAttribute("requestData", requestData);
            return new ModelAndView("redirect:/app/music/dashboard");
        }

        if(!service.isRequestCreatedInLast24hours(requestData.getInitiatedBy())){
            log.error("Trying to tamper with url: {}", auth.getName());
            attribute.addFlashAttribute("emailStatus", "Initiated failed");
            return new ModelAndView("redirect:/app/music/dashboard");
        }
        //CONVERT THE DTO TO entity and pass to service for save.
        requestData.setInitiatedBy(auth.getName());

        Optional<User> user = repo.findByUsername(auth.getName());
        user.ifPresent(value -> requestData.setSenderEmail(value.getEmail()));

        if(user.isPresent()){
            log.info("Processing confess request for user: {} with email: {}", auth.getName(), user.get().getEmail());
        }else{
            log.warn("User not found in repository: {}", auth.getName());
        }

        String result = service.buildSaveConfessData(requestData);
        if(result.equals("FAILED")){
            log.error("Failed to save confess request for user: {}", auth.getName());
            attribute.addFlashAttribute("emailStatus", "failed to Send Email");
            return new ModelAndView("redirect:/app/music/dashboard");
        }

        log.info("Confess request saved successfully for user: {}", auth.getName());
        attribute.addFlashAttribute("emailStatus", "Confess sent for Validation!");
        attribute.addFlashAttribute("requestData", requestData);
        return new ModelAndView("redirect:/app/music/dashboard?status=sentSuccess");
    }


    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @PostMapping("/connect/admin/update/request")
    public ModelAndView updateRequestStatus(ConfessDto request, RedirectAttributes redirectAttributes){
        log.info("Admin updating confess request status for room: {}", request.getRoomHash());
        log.debug("Update request details: {}", request);

        Map<String,String> response  = service.updateStatus(request);
        if(response.containsKey("error")){
            log.error("Failed to update confess request status: {}", response.get("error"));
            redirectAttributes.addFlashAttribute("errorUpdate","update failed");

        }else{
            log.info("Successfully updated confess request status to: {}", request.getStatus());
            redirectAttributes.addFlashAttribute("successUpdate",response.get("saved"));
        }

        return new ModelAndView("redirect:/app/music/connect/request/admin/inProgress");
    }
}