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
    }


    @GetMapping("/dashboard")
    public String dashboardPage(Model model, Authentication authentication) {
        String currentUser = authentication.getName();

        model.addAttribute("currentUser",currentUser);

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
        Optional<List<ConfessDto>> availableRequest;
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

            model.addAttribute("inProgressRequests",availableRequest.get());
            model.addAttribute("pendingOrCompleted",requestedSongs.get());
        }

        return "validation";
    }

    @PostMapping("/connect/sendRequest")
    public ModelAndView userRequestData(@Valid @ModelAttribute("requestData") ConfessDto requestData, Errors error, RedirectAttributes attribute, Authentication auth){
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


    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @PostMapping("/connect/admin/update/request")
    public ModelAndView updateRequestStatus(ConfessDto request, RedirectAttributes redirectAttributes){
        log.info("Requested Data : {}",request);

        Map<String,String> response  = service.updateStatus(request);
        if(response.containsKey("error")){
            redirectAttributes.addFlashAttribute("errorUpdate","update failed");

        }else{
            redirectAttributes.addFlashAttribute("successUpdate",response.get("saved"));
        }

        log.info("Finished the updating process : {}",request.getStatus());
        return new ModelAndView("redirect:/app/music/connect/request/admin/inProgress");
    }
}