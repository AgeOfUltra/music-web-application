package com.music.musicwebapplication.controller;

import com.music.musicwebapplication.chatDto.ChatMessage;
import com.music.musicwebapplication.dto.CreateRoom;
import com.music.musicwebapplication.dto.RoomJoin;
import com.music.musicwebapplication.entity.Participant;
import com.music.musicwebapplication.entity.Room;
import com.music.musicwebapplication.entity.UserSession;
import com.music.musicwebapplication.exception.RoomManageException;
import com.music.musicwebapplication.exception.RoomNotFoundException;
import com.music.musicwebapplication.service.PlaybackStateService;
import com.music.musicwebapplication.service.PublicLoginService;
import com.music.musicwebapplication.service.RoomService;
import com.music.musicwebapplication.service.UserSessionService;
import com.music.musicwebapplication.utils.JwtTokenUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.CannotAcquireLockException;
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

import java.util.*;

@Slf4j
@Controller
@RequestMapping("/app/music")
public class RoomController {
    private final RoomService rService;
    private final PublicLoginService loginService;
    private final UserSessionService sessionService;
    private final JwtTokenUtil jwtUtil;
    private final SimpMessagingTemplate simpMessagingTemplate;
    private final PlaybackStateService playbackStateService;

    public RoomController(RoomService rService, PublicLoginService loginService,
                          UserSessionService sessionService, JwtTokenUtil jwtUtil,
                          SimpMessagingTemplate simpMessagingTemplate, PlaybackStateService playbackStateService) {
        this.rService = rService;
        this.loginService = loginService;
        this.sessionService = sessionService;
        this.jwtUtil = jwtUtil;
        this.simpMessagingTemplate = simpMessagingTemplate;
        this.playbackStateService = playbackStateService;
    }

    @GetMapping("/chat")
    public String chatRoom(@RequestParam String roomName,
                           Authentication authentication,
                           Model model) {
        Optional<Room> availableRoom = rService.getRoomDetailsByHash(roomName);
        if (availableRoom.isEmpty()) {
            return "redirect:/app/music/dashboard";
        }
        Room currentRoom = availableRoom.get();
        model.addAttribute("username", authentication.getName());
        model.addAttribute("roomName", currentRoom.getRoomName());
        model.addAttribute("roomCode", roomName);
        model.addAttribute("passCode", currentRoom.getPassCode());
        model.addAttribute("roomCount", currentParticipantCount(currentRoom.getRoomName()));
        model.addAttribute("totalCount", rService.getRoomDetails(currentRoom.getRoomName()).getMaxCount());
        model.addAttribute("jwtToken", sessionService.getToken(authentication.getName()).orElse(null));
        model.addAttribute("isOrganizer", rService.isUserOrganizer(currentRoom.getRoomName(), authentication.getName()));
        return "chat";
    }

    @PostMapping("/room/create")
    public ModelAndView createRoom(@Valid @ModelAttribute("newRoom") CreateRoom newRoom, Errors error,
                                   RedirectAttributes redirectAttributes, Authentication auth) {
        if (error.hasErrors()) {
            log.error("Room validation failed due to error : {}", error);
            log.info("failed Data ! : {}", newRoom);
            redirectAttributes.addFlashAttribute("org.springframework.validation.BindingResult.newRoom", error);
            redirectAttributes.addFlashAttribute("newRoom", newRoom);
            return new ModelAndView("redirect:/app/music/dashboard");
        }

        newRoom.setCreatedBy(auth.getName());
        log.info("Room created by {} ", newRoom.getCreatedBy());

        try {
            ResponseEntity<?> response = createRoomApi(newRoom);
            if (response.getStatusCode().equals(HttpStatus.OK)) {
                Room tempRoom = (Room) response.getBody();
                log.info("what is inside the response : {} ", tempRoom.getRoomHash());
                log.info("{} room is created successfully !", newRoom);
                redirectAttributes.addFlashAttribute("roomCreatedSuccessful", "Room Created successfully");
                sessionService.updateRoomName(auth.getName(), newRoom.getRoomName());
                return new ModelAndView("redirect:/app/music/chat?roomName=" + tempRoom.getRoomHash());
            } else {
                log.error("room created failed! data : {}", response);
                redirectAttributes.addFlashAttribute("creationError", "room creation failed. Please try again.");
                redirectAttributes.addFlashAttribute("newRoom", newRoom);
                return new ModelAndView("redirect:/app/music/dashboard");
            }
        } catch (RoomManageException | RoomNotFoundException e) {
            log.error("Room creation error: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("creationError", e.getMessage());
            redirectAttributes.addFlashAttribute("newRoom", newRoom);
            return new ModelAndView("redirect:/app/music/dashboard");
        } catch (Exception e) {
            log.error("Unexpected error during room creation: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("creationError", "An unexpected error occurred: " + e.getMessage());
            redirectAttributes.addFlashAttribute("newRoom", newRoom);
            return new ModelAndView("redirect:/app/music/dashboard");
        }
    }

    private ResponseEntity<Room> createRoomApi(CreateRoom newRoom) throws Exception {
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
    public ModelAndView joinRoomByHash(@ModelAttribute("joinRoom") RoomJoin joinRoom,
                                       RedirectAttributes redirectAttributes, Authentication auth) {
        Optional<Room> availableRoom = rService.getRoomDetailsByHash(joinRoom.getRoomCode());
        if (availableRoom.isEmpty()) {
            log.info("Room joined failed!");
            redirectAttributes.addFlashAttribute("joinError", "Room Not found");
            redirectAttributes.addFlashAttribute("roomJoin", joinRoom);
            return new ModelAndView("redirect:/app/music/dashboard");
        }

        if (!availableRoom.get().getPassCode().equals(joinRoom.getPassCode())) {
            log.info("Room joined failed!");
            redirectAttributes.addFlashAttribute("joinError", "invalid room credentials");
            redirectAttributes.addFlashAttribute("roomJoin", joinRoom);
            return new ModelAndView("redirect:/app/music/dashboard");
        }

        ResponseEntity<?> response;
        joinRoom.setParticipantName(auth.getName());

        try {
            log.info("room name while joining the room by passcode {}", availableRoom.get().getRoomName());
            joinRoom.setRoomName(availableRoom.get().getRoomName());
            response = joinRoomApi(joinRoom);

            if (response.getStatusCode().equals(HttpStatus.OK)) {
                log.info("Successfully logged-in! {}", joinRoom);
                redirectAttributes.addFlashAttribute("roomJoinedSuccessful", "Joined the room successfully");
                sessionService.updateRoomName(auth.getName(), joinRoom.getRoomName());
                return new ModelAndView("redirect:/app/music/chat?roomName=" + joinRoom.getRoomCode());
            } else {
                log.error("room joined failed! data : {}", response);
                redirectAttributes.addFlashAttribute("joinError", "Unable to join Room! please try again.");
                redirectAttributes.addFlashAttribute("joinRoom", joinRoom);
                return new ModelAndView("redirect:/app/music/dashboard");
            }
        } catch (RoomManageException | RoomNotFoundException e) {
            log.error("Room join error: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("joinError", e.getMessage());
            redirectAttributes.addFlashAttribute("joinRoom", joinRoom);
            return new ModelAndView("redirect:/app/music/dashboard");
        } catch (Exception e) {
            log.error("Unexpected error during room join: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("joinError", "An unexpected error occurred: " + e.getMessage());
            redirectAttributes.addFlashAttribute("joinRoom", joinRoom);
            return new ModelAndView("redirect:/app/music/dashboard");
        }
    }

    private ResponseEntity<Participant> joinRoomApi(RoomJoin joinRoom) throws Exception {
        Participant newParticipant = new Participant();
        newParticipant.setUserName(joinRoom.getParticipantName());
        newParticipant.setOrganizer(false);
        Participant participant = rService.joinRoom(joinRoom.getRoomName(), newParticipant);
        return ResponseEntity.ok(participant);
    }

    @DeleteMapping("/room/leave")
    public ResponseEntity<Boolean> leaveRoom(@RequestParam String roomName, Authentication auth) {
        boolean isLeft = rService.exitFromRoom(roomName, auth.getName());
        return ResponseEntity.ok(isLeft);
    }

    @GetMapping("/room/getRoom")
    public ResponseEntity<Room> getRoomInformation(@RequestParam String roomName) {
        Room room = rService.getRoomDetails(roomName);
        return ResponseEntity.ok(room);
    }

    private int getAvailableParticipants(String roomName) {
        ResponseEntity<Integer> participants = getAvailableCount(roomName);
        if (participants.getStatusCode().equals(HttpStatus.OK) && participants.getBody() != null) {
            return participants.getBody();
        }
        return 0;
    }

    private ResponseEntity<Integer> getAvailableCount(String roomName) {
        Room room = rService.getRoomDetails(roomName);
        int availableCount = room.getMaxCount() - room.getParticipant().size();
        return ResponseEntity.ok(availableCount);
    }

    private int currentParticipantCount(String roomName) {
        ResponseEntity<Integer> participants = currentParticipantCountApi(roomName);
        if (participants.getStatusCode().equals(HttpStatus.OK) && participants.getBody() != null) {
            return participants.getBody();
        }
        return 0;
    }

    private ResponseEntity<Integer> currentParticipantCountApi(String roomName) {
        Room room = rService.getRoomDetails(roomName);
        int availableCount = room.getParticipant().size();
        return ResponseEntity.ok(availableCount);
    }

    @GetMapping("/room/getAllParticipants")
    public ResponseEntity<List<Participant>> getAllParticipants(String roomName) {
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

    // ==================== ✅ BEACON POST REQUEST (for page reload/close) ====================
    @PostMapping("/room/exitRoom/beacon")
    public ResponseEntity<?> exitRoomBeacon(
            @RequestParam(required = false) String token,
            @RequestParam(required = false, defaultValue = "false") boolean fullLogout,
            HttpServletRequest request) {

        log.info("🚨 Beacon POST request received - exitRoom");
        log.info("🔙 DELETE request received via Beacon - exitRoom (fullLogout={})", fullLogout);
        return exitRoomInternal(token, null, fullLogout, request);
    }

    // ==================== ✅ DELETE REQUEST (for back button) ====================
    @DeleteMapping("/room/exitRoom")
    public ResponseEntity<?> exitRoomDelete(
            @RequestParam(required = false) String token,
            @RequestParam(required = false, defaultValue = "false") boolean fullLogout,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            HttpServletRequest request) {

        log.info("🔙 DELETE request received - exitRoom (fullLogout={})", fullLogout);

        // ✅ KEY: Mark this as intentional exit (back button, not tab close)
        // Extract username first
        String jwtToken = null;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            jwtToken = authHeader.substring(7);
        } else if (token != null) {
            jwtToken = token;
        }

        if (jwtToken != null) {
            String username = jwtUtil.getUserNameFromToken(jwtToken);
            if (username != null) {
                // Mark as intentional - back button should NOT delete session
                sessionService.setIntentionalLogout(username, false);
                log.info("🔖 Marked back button exit for {}: intentionalLogout=false", username);
            }
        }

        return exitRoomInternal(token, authHeader, fullLogout, request);
    }

    // ==================== ✅ CORE EXIT LOGIC ====================
    public ResponseEntity<?> exitRoomInternal(
            String token,
            String authHeader,
            boolean fullLogout,
            HttpServletRequest request) {
        try {
            String jwtToken = null;

            // Get token from either header or query param
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                jwtToken = authHeader.substring(7);
            } else if (token != null) {
                jwtToken = token;
            } else {
                return ResponseEntity.status(HttpServletResponse.SC_UNAUTHORIZED)
                        .body(Map.of("error", "No authentication token provided"));
            }

            // Validate token
            String username = jwtUtil.getUserNameFromToken(jwtToken);
            if (username == null || !jwtUtil.validateToken(username, username, jwtToken)) {
                return ResponseEntity.status(HttpServletResponse.SC_UNAUTHORIZED)
                        .body(Map.of("error", "Invalid token"));
            }

            // Get current session
            UserSession session = sessionService.getUserSession(username);
            if (session == null) {
                log.info("ℹ️ No active session for user {}", username);
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "message", "No active session"
                ));
            }

            String roomName = session.getRoomName();
            if (roomName == null) {
                log.info("ℹ️ User {} not in any room", username);
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "message", "Not in any room"
                ));
            }

            log.info("🚪 EXIT ROOM: User {} leaving room {} (fullLogout={})",
                    username, roomName, fullLogout);

            // ✅ CRITICAL: Check intent flag to determine actual behavior
            // This overrides the fullLogout parameter for better control
            boolean shouldDelete = fullLogout; // Default to parameter value

            // If intentionalLogout flag is set, respect it
            boolean isIntentional = session.isIntentionalLogout();
            if (!isIntentional) {
                shouldDelete = false; // Back button - keep session
                log.info("🔖 Intent flag detected: keeping session for {}", username);
            }

            // Exit from room with proper session handling
            boolean removed = rService.exitFromRoom(roomName, username, shouldDelete);

            if (!removed) {
                log.warn("⚠️ User {} exit from room {} returned false (concurrent operation)",
                        username, roomName);
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "message", "Exit processed (concurrent operation)",
                        "alreadyRemoved", true
                ));
            }

            log.info("✅ User {} successfully removed from room {} (session {})",
                    username, roomName, shouldDelete ? "DELETED" : "KEPT");

// ⭐ NEW CODE STARTS HERE
// Check if the user who left was the organizer
            try {
                Room updatedRoom = rService.getRoomDetails(roomName);

                // If no organizer remains, clear playback state
                boolean hasOrganizer = updatedRoom.getParticipant().stream()
                        .anyMatch(Participant::isOrganizer);

                if (!hasOrganizer) {
                    log.info("🎵 No organizer in room {} - clearing playback state", roomName);
                    playbackStateService.clearPlaybackState(roomName);

                    // Broadcast STOP action to all remaining participants
                    Map<String, Object> stopMessage = new HashMap<>();
                    stopMessage.put("action", "STOP");
                    stopMessage.put("controller", "SYSTEM");
                    stopMessage.put("timestamp", 0L);

                    simpMessagingTemplate.convertAndSend(
                            "/topic/chat/" + roomName + "/playback",
                            stopMessage
                    );

                    log.info("✅ Cleared playback state and broadcasted STOP for room: {}", roomName);
                }
            } catch (RoomNotFoundException e) {
                log.info("ℹ️ Room {} no longer exists after organizer left", roomName);
            } catch (Exception e) {
                log.error("❌ Error clearing playback state: {}", e.getMessage());
            }
// ⭐ NEW CODE ENDS HERE

// Reset the intent flag after processing
            sessionService.resetIntentionalLogout(username);

            // Try to broadcast updates
            try {
                Room updatedRoom = rService.getRoomDetails(roomName);

                simpMessagingTemplate.convertAndSend(
                        "/topic/chat/" + roomName + "/participants",
                        updatedRoom.getParticipant()
                );

                ChatMessage leaveMsg = new ChatMessage();
                leaveMsg.setSender(username);
                leaveMsg.setType(String.valueOf(ChatMessage.MessageType.LEAVE));
                leaveMsg.setContent(username + " left the room");
                leaveMsg.setTimestamp(System.currentTimeMillis());

                simpMessagingTemplate.convertAndSend("/topic/chat/" + roomName, leaveMsg);

                log.info("✅ Broadcasted exit notifications for user {} in room {}", username, roomName);

            } catch (RoomNotFoundException e) {
                log.info("ℹ️ Room {} no longer exists (last organizer left)", roomName);
            } catch (Exception e) {
                log.error("❌ Error broadcasting WebSocket notifications: {}", e.getMessage());
            }

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Successfully exited room",
                    "roomName", roomName,
                    "sessionDeleted", shouldDelete
            ));

        } catch (CannotAcquireLockException e) {
            log.warn("⚠️ Database deadlock in exitRoom (concurrent operations) - treating as success: {}",
                    e.getMessage());
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Exit processed by concurrent operation",
                    "note", "Deadlock avoided"
            ));

        } catch (Exception e) {
            log.error("❌ Error in exitRoom: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "success", false,
                            "error", "Error exiting room: " + e.getMessage()
                    ));
        }
    }
}