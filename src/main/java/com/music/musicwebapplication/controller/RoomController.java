package com.music.musicwebapplication.controller;

import com.music.musicwebapplication.dto.CreateRoom;
import com.music.musicwebapplication.dto.JoinRoom;
import com.music.musicwebapplication.entity.Participant;
import com.music.musicwebapplication.entity.Room;
import com.music.musicwebapplication.exception.RoomManageException;
import com.music.musicwebapplication.exception.RoomNotFoundException;
import com.music.musicwebapplication.service.RoomService;
import com.music.musicwebapplication.utils.ColorUsageUtil;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.Errors;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Controller
@RequestMapping("/app/music")
public class RoomController {
    private final RoomService rService;
    private final PublicSongController publicSongController;
    private final ColorUsageUtil colorUsageUtil;
    private final SimpMessagingTemplate messagingTemplate;
    public RoomController(RoomService rService, PublicSongController publicSongController, ColorUsageUtil colorUsageUtil, SimpMessagingTemplate messagingTemplate){

        this.rService = rService;
        this.publicSongController = publicSongController;
        this.colorUsageUtil = colorUsageUtil;
        this.messagingTemplate = messagingTemplate;
    }

    @GetMapping("/chat")
    public String chatRoom(@RequestParam String roomName,
                           Authentication authentication,
                           Model model, HttpSession session) {
        model.addAttribute("username", authentication.getName());
        model.addAttribute("roomName", roomName);
        model.addAttribute("ALLSONGS",publicSongController.getAllSongs());
        model.addAttribute("roomCount", currentParticipantCount(roomName));
        model.addAttribute("totalCount", rService.getRoomDetails(roomName).getMaxCount());
        model.addAttribute("participants", getParticipants(roomName));
        model.addAttribute("userColor",colorUsageUtil.getUserColors(authentication.getName()).get("userColor"));
        model.addAttribute("darkerColor",colorUsageUtil.getUserColors(authentication.getName()).get("darkerColor"));
        model.addAttribute("jwtToken",session.getAttribute("jwtToken"));
        model.addAttribute("isOrganizer",rService.isUserOrganizer(roomName,authentication.getName()));
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
                log.info("{} room is created successfully !", newRoom);
                redirectAttributes.addFlashAttribute("roomCreatedSuccessful",true); // need to show in the chat.html
                return new ModelAndView("redirect:/app/music/chat?roomName="+newRoom.getRoomName());
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
//    Actual method for join room
    public ModelAndView joinRoom(@ModelAttribute("joinRoom")JoinRoom joinRoom,RedirectAttributes redirectAttributes,Authentication auth) {
        ResponseEntity<?> response;
        joinRoom.setParticipantName(auth.getName());
        try{
            response = joinRoomApi(joinRoom);
            if(response.getStatusCode().equals(HttpStatus.OK)) {
                log.info("Successfully logged-in! {}",joinRoom);
                redirectAttributes.addFlashAttribute("roomJoinedSuccessful",true);
                return new ModelAndView("redirect:/app/music/chat?roomName="+ joinRoom.getRoomName());
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

    private void broadcastParticipantUpdate(String roomName) {
        try {
            Room room = rService.getRoomDetails(roomName);
            messagingTemplate.convertAndSend(
                    "/topic/chat/" + roomName + "/participants",
                    room.getParticipant()
            );
        } catch (Exception e) {
            log.error("Error broadcasting participant update", e);
        }
    }


//    API
    private ResponseEntity<Participant> joinRoomApi(JoinRoom joinRoom) throws Exception{
        Participant newParticipant = new Participant();
        newParticipant.setUserName(joinRoom.getParticipantName());
        newParticipant.setOrganizer(false);
        Participant participant = rService.joinRoom(joinRoom.getRoomName(),newParticipant);
        return ResponseEntity.ok(participant);
    }

    //upon logout or participant leave from the room
    @DeleteMapping("/room/leave")
    public ResponseEntity<Boolean> leaveRoom(@RequestParam String roomName, @RequestParam String username){
        boolean isLeft = rService.exitFromRoom(roomName,username);
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

    private List<Participant> getParticipants(String roomName){
        ResponseEntity<List<Participant>> participants = getAllParticipants(roomName);
        List<Participant> availableParticipants = null;
        if(participants.getStatusCode().equals(HttpStatus.OK)){
            availableParticipants = participants.getBody();

        }
        return availableParticipants;
    }

//    API to get all participants in a room
    @GetMapping("/room/getAllParticipants")
    private ResponseEntity<List<Participant>> getAllParticipants(String roomName){
        Room room = rService.getRoomDetails(roomName);
        List<Participant> participants = room.getParticipant();
        return ResponseEntity.ok(participants);
    }



}