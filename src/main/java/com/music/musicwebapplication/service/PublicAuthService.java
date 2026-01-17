package com.music.musicwebapplication.service;

import com.music.musicwebapplication.dto.LoginUser;
import com.music.musicwebapplication.entity.Room;
import com.music.musicwebapplication.entity.User;
import com.music.musicwebapplication.entity.UserSession;
import com.music.musicwebapplication.exception.RoomNotFoundException;
import com.music.musicwebapplication.repo.UserRepo;
import com.music.musicwebapplication.utils.JwtTokenUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
public class PublicAuthService {
    private final AuthenticationManager authenticationManager;

    private final JwtTokenUtil jwtUtil;
    private final RoomService roomService;
    private final SimpMessagingTemplate simpMessagingTemplate;
    private final UserSessionService sessionService;
    private final UserRepo repo;


    public PublicAuthService(AuthenticationManager authenticationManager, JwtTokenUtil jwtUtil, RoomService roomService, SimpMessagingTemplate simpMessagingTemplate, UserSessionService sessionService, UserRepo repo) {
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
        this.roomService = roomService;
        this.simpMessagingTemplate = simpMessagingTemplate;
        this.sessionService = sessionService;
        this.repo = repo;
    }

    public String extractUsernameFromJwt(HttpServletRequest request) {
        String cookieHeader = request.getHeader("Cookie");
        if (cookieHeader != null) {
            for (String c : cookieHeader.split(";")) {
                String trimmed = c.trim();
                if (trimmed.startsWith("jwt=")) {
                    String token = trimmed.substring("jwt=".length());
                    return jwtUtil.getIdentityFromToken(token);
                }
            }
        }
        return null;
    }

// This handles BOTH scenarios:
// 1. User in room → Exit room + delete session
// 2. User NOT in room → Just delete session

    public ResponseEntity<?> authenticate(LoginUser loginUser) {
        Map<String, Object> response = new HashMap<>();
        try {



            Optional<User> currentUse = repo.findByUsername(loginUser.getUsername());
//            Case 1 : User not registered.
            if(currentUse.isEmpty()){
                response.put("UserError", "Try gain After SingUp");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }

//            Case 2 : User registered but not verified
            if(!currentUse.get().isVerified()){
                response.put("UserError", "Kindly Validate the your account");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
            }
//Case 3 : User registered and Verified, if already logged in
            Optional<UserSession> loggedUser = Optional.ofNullable(sessionService.getUserSession(loginUser.getUsername()));

            if (loggedUser.isPresent()) {
                response.put("UserError", "User already logged In!");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response); // <— map, not String
            }


//            successful Case : User Registered, Verified, and First time logging

            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            loginUser.getUsername(), loginUser.getPassword())
            );
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            String token = jwtUtil.generateToken(userDetails.getUsername());
            String username = userDetails.getUsername();

            response.put("token", token);
            response.put("username", username);


            boolean isSaved = sessionService.saveSession(token, username);
            if (!isSaved) {
                response.put("error", "Unexpected Error! Try again after Some time");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
            }
            response.put("message", "Login successful");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("error", "Invalid credentials");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }
    }

    public boolean logout(UserSession session) {
        if (session == null) {
            log.error("❌ Cannot logout: session is null");
            return false;
        }

        boolean success = true;
        String username = session.getUsername();
        String roomName = session.getRoomName();

        log.info("🚪 LOGOUT: User {} logging out", username);

        try {
            if (roomName != null ) {
                // ==================== SCENARIO 1: USER IN ROOM ====================
                log.info("🏠 User {} is in room {}, exiting with {} logout...", username, roomName, session.isIntentionalLogout() ?"FULL" : "PARTIAL");

                try {
                    // ✅ Exit room with fullLogout=true (deletes session)
                    boolean removed = roomService.exitFromRoom(roomName, username, session.isIntentionalLogout());

                    if (removed) {
                        log.info("✅ User {} exited room {} (session deleted)", username, roomName);

                        // Broadcast to remaining participants
                        try {
                            Room updatedRoom = roomService.getRoomDetails(roomName);

                            simpMessagingTemplate.convertAndSend(
                                    "/topic/chat/" + roomName + "/participants",
                                    updatedRoom.getParticipant()
                            );

                            log.info("✅ Broadcasted participant update for room {}", roomName);

                        } catch (RoomNotFoundException e) {
                            // Room deleted (last organizer) - this is expected behavior
                            log.info("ℹ️ Room {} deleted (last organizer left)", roomName);
                        } catch (Exception e) {
                            log.error("❌ Error broadcasting participant update: {}", e.getMessage());
                            success = false; // Non-critical error, but mark as partial failure
                        }
                    } else {
                        log.error("❌ Failed to remove user {} from room {}", username, roomName);
                        success = false;
                    }

                } catch (Exception e) {
                    log.error("❌ Error exiting room during logout: {}", e.getMessage(), e);
                    success = false;

                    // ⚠️ CRITICAL: Ensure session is deleted even if room exit fails
                    try {
                        sessionService.deleteUserSession(username);
                        log.info("✅ Session deleted as fallback after room exit error");
                    } catch (Exception ex) {
                        log.error("❌ CRITICAL: Failed to delete session as fallback: {}", ex.getMessage(), ex);
                        return false; // Total failure
                    }
                }

            } else {
                // ==================== SCENARIO 2: USER NOT IN ROOM ====================
                log.info("📋 User {} not in any room, deleting session only", username);

                try {
                    sessionService.deleteUserSession(username);
                    log.info("✅ Session deleted successfully");
                } catch (Exception e) {
                    log.error("❌ Error deleting session: {}", e.getMessage(), e);
                    return false; // Critical failure - session not deleted
                }
            }

            if (success) {
                log.info("✅ User {} logout complete (fully successful)", username);
            } else {
                log.warn("⚠️ User {} logout complete (with some errors)", username);
            }

            return success;

        } catch (Exception e) {
            log.error("❌ Unexpected error during logout for user {}: {}", username, e.getMessage(), e);

            // Last-ditch effort to clean up session
            try {
                sessionService.deleteUserSession(username);
                log.info("✅ Session deleted in final error recovery");
                return false; // Cleanup done but there were errors
            } catch (Exception ex) {
                log.error("❌ CRITICAL: Failed to delete session in final error recovery: {}", ex.getMessage(), ex);
                return false; // Total failure
            }
        }
    }
}
