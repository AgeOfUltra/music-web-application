package com.music.musicwebapplication.controller;

import com.music.musicwebapplication.dto.CreateRoom;
import com.music.musicwebapplication.dto.RoomJoin;
import com.music.musicwebapplication.entity.Participant;
import com.music.musicwebapplication.entity.Room;
import com.music.musicwebapplication.exception.RoomManageException;
import com.music.musicwebapplication.exception.RoomNotFoundException;
import com.music.musicwebapplication.service.PublicLoginService;
import com.music.musicwebapplication.service.RoomService;
import com.music.musicwebapplication.service.UserSessionService;
import com.music.musicwebapplication.utils.ColorUsageUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.Errors;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Controller
@RequestMapping("/app/music")
public class RoomController {
    private final RoomService rService;
    private final ColorUsageUtil colorUsageUtil;
    private final PublicLoginService loginService;
    private final UserSessionService sessionService;
    public RoomController(RoomService rService, ColorUsageUtil colorUsageUtil, PublicLoginService loginService, UserSessionService sessionService){

        this.rService = rService;
        this.colorUsageUtil = colorUsageUtil;
        this.loginService = loginService;
        this.sessionService = sessionService;
    }

    @GetMapping("/chat")
    public String chatRoom(@RequestParam String roomName,
                           Authentication authentication,
                           Model model) {
        Optional<Room> availableRoom = rService.getRoomDetailsByHash(roomName);
        if(availableRoom.isEmpty()){
            return "redirect:/app/music/dashboard";
        }
        Room currentRoom = availableRoom.get();
        model.addAttribute("username", authentication.getName());
        model.addAttribute("roomName", currentRoom.getRoomName());
        model.addAttribute("roomCode", roomName);
        model.addAttribute("passCode",currentRoom.getPassCode());
        model.addAttribute("roomCount", currentParticipantCount(currentRoom.getRoomName()));
        model.addAttribute("totalCount", rService.getRoomDetails(currentRoom.getRoomName()).getMaxCount());
        model.addAttribute("userColor",colorUsageUtil.getUserColors(authentication.getName()).get("userColor"));
        model.addAttribute("darkerColor",colorUsageUtil.getUserColors(authentication.getName()).get("darkerColor"));
        model.addAttribute("jwtToken",sessionService.getToken(authentication.getName()).orElse(null));
        model.addAttribute("isOrganizer",rService.isUserOrganizer(currentRoom.getRoomName(),authentication.getName()));
        return "chat";
    }

//        Actual method
    @PostMapping("/room/create")
    public ModelAndView createRoom(@Valid @ModelAttribute("newRoom") CreateRoom newRoom, Errors error, RedirectAttributes redirectAttributes, Authentication auth) {

        if(error.hasErrors()){
            log.error("Room validation failed due to error : {}", error);
            log.info("failed Data ! : {}", newRoom);
            redirectAttributes.addFlashAttribute("org.springframework.validation.BindingResult.newRoom", error);
            redirectAttributes.addFlashAttribute("newRoom", newRoom);
            return new ModelAndView("redirect:/app/music/dashboard");
        }
        newRoom.setCreatedBy(auth.getName());
        log.info("Room created by {} ",newRoom.getCreatedBy());
        try {
            ResponseEntity<?> response = createRoomApi(newRoom);
            if(response.getStatusCode().equals(HttpStatus.OK)){
                Room tempRoom = (Room) response.getBody();
                log.info("what is inside the response : {} ",tempRoom.getRoomHash());
                log.info("{} room is created successfully !", newRoom);
                redirectAttributes.addFlashAttribute("roomCreatedSuccessful","Room Created successfully"); // need to show in the chat.html
                sessionService.updateRoomName(auth.getName(), newRoom.getRoomName());
                return new ModelAndView("redirect:/app/music/chat?roomName="+tempRoom.getRoomHash());
            }else{
                log.error("room created failed! data : {}", response);
                log.info("room created failed! data : {}", response);
                redirectAttributes.addFlashAttribute("creationError","room creation failed. Please try again."); // need to show in dashboard.html
                redirectAttributes.addFlashAttribute("newRoom", newRoom);
                return new ModelAndView("redirect:/app/music/dashboard");
            }

        } catch (RoomManageException | RoomNotFoundException e) {
            log.error("Room creation error: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("creationError", e.getMessage());
            redirectAttributes.addFlashAttribute("newRoom", newRoom);
            return new ModelAndView("redirect:/app/music/dashboard");

        }
        catch (Exception e) {
            log.error("Unexpected error during room creation: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("creationError", "An unexpected error occurred: " + e.getMessage());
            redirectAttributes.addFlashAttribute("newRoom", newRoom);
            return new ModelAndView("redirect:/app/music/dashboard");
        }


    }
//    API
    private ResponseEntity<Room> createRoomApi(CreateRoom newRoom ) throws Exception{
        Room roomBuild = new Room();
        roomBuild.setRoomName(newRoom.getRoomName());
        roomBuild.setMaxCount(newRoom.getMaxCount());
        Participant participant = new Participant();
        participant.setUserName(newRoom.getCreatedBy());
        participant.setOrganizer(true);
        List<Participant> participants = new ArrayList<>();
        participants.add(participant);
        roomBuild.setParticipant(participants);
        Room room = rService.createRoom(roomBuild);
        return ResponseEntity.ok(room);

    }

    @PostMapping("/room/join")
    public ModelAndView joinRoomByHash(@ModelAttribute("joinRoom")RoomJoin joinRoom, RedirectAttributes redirectAttributes,Authentication auth){
//        get the roomUsing passCode and to validate.

        Optional<Room> availableRoom = rService.getRoomDetailsByHash(joinRoom.getRoomCode());
        if(availableRoom.isEmpty()){
            log.info("Room joined failed!");
            redirectAttributes.addFlashAttribute("joinError", "Room Not found");
            redirectAttributes.addFlashAttribute("roomJoin", joinRoom);
            return new ModelAndView("redirect:/app/music/dashboard");
        }
        if(!availableRoom.get().getPassCode().equals(joinRoom.getPassCode())){
            log.info("Room joined failed!");
            redirectAttributes.addFlashAttribute("joinError", "invalid room credentials");
            redirectAttributes.addFlashAttribute("roomJoin", joinRoom);
            return new ModelAndView("redirect:/app/music/dashboard");
        }

        ResponseEntity<?> response;
        joinRoom.setParticipantName(auth.getName());
        try{
            log.info("room name while joining the room by passcode {}",availableRoom.get().getRoomName());
            joinRoom.setRoomName(availableRoom.get().getRoomName());
            response = joinRoomApi(joinRoom);
            if(response.getStatusCode().equals(HttpStatus.OK)) {
                log.info("Successfully logged-in! {}",joinRoom);
                redirectAttributes.addFlashAttribute("roomJoinedSuccessful","Joined the room successfully");
                sessionService.updateRoomName(auth.getName(), joinRoom.getRoomName());
                return new ModelAndView("redirect:/app/music/chat?roomName="+ joinRoom.getRoomCode());
            }else{
                log.error("room joined failed! data : {}", response);
                redirectAttributes.addFlashAttribute("joinError", "Unable to join Room! please try again.");
                redirectAttributes.addFlashAttribute("joinRoom", joinRoom);
                return new ModelAndView("redirect:/app/music/dashboard");
            }
        }catch (RoomManageException | RoomNotFoundException e){
            log.error("Room join error: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("joinError", e.getMessage());
            redirectAttributes.addFlashAttribute("joinRoom", joinRoom);
            return new ModelAndView("redirect:/app/music/dashboard");
        } catch(Exception e){
            log.error("Unexpected error during room join: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("joinError", "An unexpected error occurred: " + e.getMessage());
            redirectAttributes.addFlashAttribute("joinRoom", joinRoom);
            return new ModelAndView("redirect:/app/music/dashboard");
        }
    }
//    API

    private ResponseEntity<Participant> joinRoomApi(RoomJoin joinRoom) throws Exception{
        Participant newParticipant = new Participant();
        newParticipant.setUserName(joinRoom.getParticipantName());
        newParticipant.setOrganizer(false);
        Participant participant = rService.joinRoom(joinRoom.getRoomName(),newParticipant);
        return ResponseEntity.ok(participant);
    }

    //upon logout or participant leave from the room
    @DeleteMapping("/room/leave")
    public ResponseEntity<Boolean> leaveRoom(@RequestParam String roomName, Authentication auth) {
        boolean isLeft = rService.exitFromRoom(roomName, auth.getName());
        return ResponseEntity.ok(isLeft);
    }

    @GetMapping("/room/getRoom")
    public ResponseEntity<Room> getRoomInformation(@RequestParam String roomName){
        Room room = rService.getRoomDetails(roomName);
        return ResponseEntity.ok(room);

    }

    private int getAvailableParticipants(String roomName){
        ResponseEntity<Integer> participants = getAvailableCount(roomName);
        if(participants.getStatusCode().equals(HttpStatus.OK) && participants.getBody() != null){
            return participants.getBody();

        }
        return 0;
    }

//    @GetMapping("/room/getAvailability")
    private ResponseEntity<Integer> getAvailableCount(String roomName){
        Room room = rService.getRoomDetails(roomName);
        int availableCount = room.getMaxCount() - room.getParticipant().size();
        return ResponseEntity.ok(availableCount);
    }

    private int currentParticipantCount(String roomName){
        ResponseEntity<Integer> participants = currentParticipantCountApi(roomName);
        if(participants.getStatusCode().equals(HttpStatus.OK) && participants.getBody() != null){
            return participants.getBody();
        }
        return 0;
    }

    private ResponseEntity<Integer> currentParticipantCountApi(String roomName){

        Room room = rService.getRoomDetails(roomName);
        int availableCount =  room.getParticipant().size();
        return ResponseEntity.ok(availableCount);
    }


//    API to get all participants in a room
    @GetMapping("/room/getAllParticipants")
    public ResponseEntity<List<Participant>> getAllParticipants(String roomName){
        Room room = rService.getRoomDetails(roomName);
        List<Participant> participants = room.getParticipant();
        return ResponseEntity.ok(participants);
    }

    @PostMapping("/room/clearRoomSession")
    public ResponseEntity<?> clearRoomSession(HttpServletRequest request) {

        String username = loginService.extractUsernameFromJwt(request);

        if (username != null) {
            sessionService.updateRoomName(username, null);
        }

        return ResponseEntity.ok("Room cleared");
    }

    @DeleteMapping("/room/exitRoom")
    public ResponseEntity<?> exitRoom(HttpServletRequest request) {

        String username = loginService.extractUsernameFromJwt(request);

        if (username == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("success", false, "message", "Unauthorized"));
        }

        try {
            // ✅ SCENARIO 1: Back button - keep session, clear room only
            Optional<String> leftRoom = rService.exitFromRoomWithNotification(username, false);

            if (leftRoom.isEmpty()) {
                return ResponseEntity.ok().body(Map.of(
                        "success", true,
                        "message", "Not in any room or already exited"
                ));
            }

            log.info("✅ User {} exited room {} (BACK button - session kept)", username, leftRoom.get());

            return ResponseEntity.ok().body(Map.of(
                    "success", true,
                    "message", "Successfully exited from room",
                    "roomName", leftRoom.get()
            ));

        } catch (Exception e) {
            log.error("Error exiting room for user {}: {}", username, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "Error exiting room"));
        }
    }


}