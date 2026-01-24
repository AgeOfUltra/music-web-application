package com.music.musicwebapplication.controller;

import com.music.musicwebapplication.dto.CreateRoom;
import com.music.musicwebapplication.dto.RoomJoin;
import com.music.musicwebapplication.entity.Participant;
import com.music.musicwebapplication.entity.Room;
import com.music.musicwebapplication.exception.RoomManageException;
import com.music.musicwebapplication.exception.RoomNotFoundException;
import com.music.musicwebapplication.service.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.propertyeditors.StringTrimmerEditor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.Errors;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.*;

@Slf4j
@Controller
@RequestMapping("/app/music")
public class RoomController {
    private final RoomService rService;
    private final PublicAuthService loginService;
    private final UserSessionService sessionService;

    public RoomController(RoomService rService, PublicAuthService loginService,
                          UserSessionService sessionService) {
        this.rService = rService;
        this.loginService = loginService;
        this.sessionService = sessionService;
        log.debug("RoomController initialized");
    }

    @GetMapping("/chat")
    public String chatRoom(@RequestParam String roomName,
                           Authentication authentication,
                           Model model) {
        log.info("Chat room access request for roomName: {} by user: {}", roomName, authentication.getName());

        Optional<Room> availableRoom = rService.getRoomDetailsByHash(roomName);
        if (availableRoom.isEmpty()) {
            log.warn("Room not found for hash: {}, redirecting to dashboard", roomName);
            return "redirect:/app/music/dashboard";
        }

        Room currentRoom = availableRoom.get();
        log.debug("Room found - name: {}, code: {}", currentRoom.getRoomName(), roomName);

        model.addAttribute("username", authentication.getName());
        model.addAttribute("roomName", currentRoom.getRoomName());
        model.addAttribute("roomCode", roomName);
        model.addAttribute("expireAt",sessionService.getUserSession(authentication.getName()).getAbsoluteExpiry());
        model.addAttribute("passCode", currentRoom.getPassCode());
        model.addAttribute("roomCount", currentParticipantCount(currentRoom.getRoomName()));
        model.addAttribute("totalCount", rService.getRoomDetails(currentRoom.getRoomName()).getMaxCount());
        model.addAttribute("jwtToken", sessionService.getToken(authentication.getName()).orElse(null));
        model.addAttribute("isOrganizer", rService.isUserOrganizer(currentRoom.getRoomName(), authentication.getName()));

        log.info("Chat room page prepared for user: {} in room: {}", authentication.getName(), currentRoom.getRoomName());
        return "chat";
    }

    @InitBinder
    public void initBinder(WebDataBinder binder) {
        binder.registerCustomEditor(
                String.class,
                new StringTrimmerEditor(true) // trims + converts "" to null
        );
    }

    @PostMapping("/room/create")
    public ModelAndView createRoom(@Valid @ModelAttribute("newRoom") CreateRoom newRoom, Errors error,
                                   RedirectAttributes redirectAttributes, Authentication auth) {
        log.info("Room creation request received from user: {}", auth.getName());
        log.debug("Room creation data: {}", newRoom);

        if (error.hasErrors()) {
            log.error("Room creation validation failed for user {}: {}", auth.getName(), error);
            log.debug("Failed room data: {}", newRoom);
            redirectAttributes.addFlashAttribute("org.springframework.validation.BindingResult.newRoom", error);
            redirectAttributes.addFlashAttribute("newRoom", newRoom);
            return new ModelAndView("redirect:/app/music/dashboard");
        }

        newRoom.setCreatedBy(auth.getName());
        newRoom.setRoomName(newRoom.getRoomName().trim());
        log.debug("Room name trimmed: {}", newRoom.getRoomName());

        try {
            ResponseEntity<?> response = createRoomApi(newRoom);
            if (response.getStatusCode().equals(HttpStatus.OK)) {
                Room tempRoom = (Room) response.getBody();
                log.info("Room created successfully by user: {}, room hash: {}", auth.getName(), tempRoom.getRoomHash());
                redirectAttributes.addFlashAttribute("roomCreatedSuccessful", "Room Created successfully");
                sessionService.updateRoomName(auth.getName(), newRoom.getRoomName());
                return new ModelAndView("redirect:/app/music/chat?roomName=" + tempRoom.getRoomHash());
            } else {
                log.error("Room creation failed for user {}: {}", auth.getName(), response);
                redirectAttributes.addFlashAttribute("creationError", "room creation failed. Please try again.");
                redirectAttributes.addFlashAttribute("newRoom", newRoom);
                return new ModelAndView("redirect:/app/music/dashboard");
            }
        } catch (RoomManageException | RoomNotFoundException e) {
            log.error("Room creation error for user {}: {}", auth.getName(), e.getMessage());
            redirectAttributes.addFlashAttribute("creationError", e.getMessage());
            redirectAttributes.addFlashAttribute("newRoom", newRoom);
            return new ModelAndView("redirect:/app/music/dashboard");
        } catch (Exception e) {
            log.error("Unexpected error during room creation for user {}: {}", auth.getName(), e.getMessage(), e);
            redirectAttributes.addFlashAttribute("creationError", "An unexpected error occurred: ");
            redirectAttributes.addFlashAttribute("newRoom", newRoom);
            return new ModelAndView("redirect:/app/music/dashboard");
        }
    }

    private ResponseEntity<Room> createRoomApi(CreateRoom newRoom) throws Exception {
        log.debug("Creating room API call for room: {}", newRoom.getRoomName());
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
        log.debug("Room created via API: {}", room.getRoomName());
        return ResponseEntity.ok(room);
    }

    @PostMapping("/room/join")
    public ModelAndView joinRoomByHash(@ModelAttribute("joinRoom") RoomJoin joinRoom,
                                       RedirectAttributes redirectAttributes, Authentication auth) {
        log.info("Room join request received from user: {} for room code: {}", auth.getName(), joinRoom.getRoomCode());

        Optional<Room> availableRoom = rService.getRoomDetailsByHash(joinRoom.getRoomCode());
        if (availableRoom.isEmpty()) {
            log.warn("Room join failed - room not found for code: {}", joinRoom.getRoomCode());
            redirectAttributes.addFlashAttribute("joinError", "Room Not found");
            redirectAttributes.addFlashAttribute("roomJoin", joinRoom);
            return new ModelAndView("redirect:/app/music/dashboard");
        }

        joinRoom.setRoomCode(joinRoom.getRoomCode().trim());
        if (!availableRoom.get().getPassCode().equals(joinRoom.getPassCode())) {
            log.warn("Room join failed - invalid passcode for user: {}, room code: {}", auth.getName(), joinRoom.getRoomCode());
            redirectAttributes.addFlashAttribute("joinError", "invalid room credentials");
            redirectAttributes.addFlashAttribute("roomJoin", joinRoom);
            return new ModelAndView("redirect:/app/music/dashboard");
        }

        ResponseEntity<?> response;
        joinRoom.setParticipantName(auth.getName());

        try {
            log.debug("Room name while joining: {}", availableRoom.get().getRoomName());
            joinRoom.setRoomName(availableRoom.get().getRoomName());
            response = joinRoomApi(joinRoom);

            if (response.getStatusCode().equals(HttpStatus.OK)) {
                log.info("User {} successfully joined room: {}", auth.getName(), joinRoom.getRoomName());
                redirectAttributes.addFlashAttribute("roomJoinedSuccessful", "Joined the room successfully");
                sessionService.updateRoomName(auth.getName(), joinRoom.getRoomName());
                return new ModelAndView("redirect:/app/music/chat?roomName=" + joinRoom.getRoomCode());
            } else {
                log.error("Room join failed for user {}: {}", auth.getName(), response);
                redirectAttributes.addFlashAttribute("joinError", "Unable to join Room! please try again.");
                redirectAttributes.addFlashAttribute("joinRoom", joinRoom);
                return new ModelAndView("redirect:/app/music/dashboard");
            }
        } catch (RoomManageException | RoomNotFoundException e) {
            log.error("Room join error for user {}: {}", auth.getName(), e.getMessage());
            redirectAttributes.addFlashAttribute("joinError", e.getMessage());
            redirectAttributes.addFlashAttribute("joinRoom", joinRoom);
            return new ModelAndView("redirect:/app/music/dashboard");
        } catch (Exception e) {
            log.error("Unexpected error during room join for user {}: {}", auth.getName(), e.getMessage(), e);
            redirectAttributes.addFlashAttribute("joinError", "An unexpected error occurred");
            redirectAttributes.addFlashAttribute("joinRoom", joinRoom);
            return new ModelAndView("redirect:/app/music/dashboard");
        }
    }

    private ResponseEntity<Participant> joinRoomApi(RoomJoin joinRoom) throws Exception {
        log.debug("Joining room API call for user: {} in room: {}", joinRoom.getParticipantName(), joinRoom.getRoomName());
        Participant newParticipant = new Participant();
        newParticipant.setUserName(joinRoom.getParticipantName());
        newParticipant.setOrganizer(false);
        Participant participant = rService.joinRoom(joinRoom.getRoomName(), newParticipant);
        log.debug("User {} joined room via API: {}", joinRoom.getParticipantName(), joinRoom.getRoomName());
        return ResponseEntity.ok(participant);
    }

    @DeleteMapping("/room/leave")
    public ResponseEntity<Boolean> leaveRoom(@RequestParam String roomName, Authentication auth) {
        log.info("Leave room request received from user: {} for room: {}", auth.getName(), roomName);
        boolean isLeft = rService.exitFromRoom(roomName, auth.getName());
        log.info("User {} left room {}: {}", auth.getName(), roomName, isLeft ? "success" : "failed");
        return ResponseEntity.ok(isLeft);
    }

    @GetMapping("/room/getRoom")
    public ResponseEntity<Room> getRoomInformation(@RequestParam String roomName) {
        log.debug("Get room information request for room: {}", roomName);
        Room room = rService.getRoomDetails(roomName);
        return ResponseEntity.ok(room);
    }



    private int currentParticipantCount(String roomName) {
        log.debug("Fetching current participant count for room: {}", roomName);
        ResponseEntity<Integer> participants = currentParticipantCountApi(roomName);
        if (participants.getStatusCode().equals(HttpStatus.OK) && participants.getBody() != null) {
            log.debug("Participant count for room {}: {}", roomName, participants.getBody());
            return participants.getBody();
        }
        log.warn("Failed to get participant count for room: {}", roomName);
        return 0;
    }

    private ResponseEntity<Integer> currentParticipantCountApi(String roomName) {
        Room room = rService.getRoomDetails(roomName);
        int availableCount = room.getParticipant().size();
        return ResponseEntity.ok(availableCount);
    }

    @GetMapping("/room/getAllParticipants")
    public ResponseEntity<List<Participant>> getAllParticipants(String roomName) {
        log.debug("Get all participants request for room: {}", roomName);
        Room room = rService.getRoomDetails(roomName);
        List<Participant> participants = room.getParticipant();
        log.debug("Retrieved {} participants for room: {}", participants.size(), roomName);
        return ResponseEntity.ok(participants);
    }

    @PostMapping("/room/clearRoomSession")
    public ResponseEntity<?> clearRoomSession(HttpServletRequest request) {
        log.debug("Clear room session request received");
        String username = loginService.extractUsernameFromJwt(request);
        if (username != null) {
            log.info("Clearing room session for user: {}", username);
            sessionService.updateRoomName(username, null);
        } else {
            log.warn("Clear room session failed - no username found in JWT");
        }
        return ResponseEntity.ok("Room cleared");
    }

    @PostMapping("/room/update/session/flag")
    public ResponseEntity<String> updateFlagForUser(@RequestParam("token") String token,@RequestParam("flag") boolean flag){
//        log.info("Update session flag request received - flag: {}", flag);
        String result  = sessionService.updateRequestFlagStatus(token,flag);
        log.info("Session flag update result: {}", result);
        return  ResponseEntity.status(HttpStatus.OK).body(result);
    }
}