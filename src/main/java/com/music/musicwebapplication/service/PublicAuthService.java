package com.music.musicwebapplication.service;

import com.music.musicwebapplication.dto.LoginUser;
import com.music.musicwebapplication.dto.RegisterUser;
import com.music.musicwebapplication.entity.Room;
import com.music.musicwebapplication.entity.User;
import com.music.musicwebapplication.entity.UserSession;
import com.music.musicwebapplication.exception.RoomNotFoundException;
import com.music.musicwebapplication.repo.UserRepo;
import com.music.musicwebapplication.utils.JwtTokenUtil;
import jakarta.mail.MessagingException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class PublicAuthService {
    @Value("${app.music.base.url}")
    private String baseUrl;

    private final UserRepo repo;
    private final ModelMapper mapper;
    private final PasswordEncoder encoder;
    private final EmailAgentService emailService;
    private final TokenService tokenService;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenUtil jwtUtil;
    private final RoomService roomService;
    private final SimpMessagingTemplate simpMessagingTemplate;
    private final UserSessionService sessionService;



    public PublicAuthService(AuthenticationManager authenticationManager, JwtTokenUtil jwtUtil, RoomService roomService, SimpMessagingTemplate simpMessagingTemplate, UserSessionService sessionService, UserRepo repo, ModelMapper mapper, PasswordEncoder encoder, EmailAgentService emailService, TokenService tokenService) {
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
        this.roomService = roomService;
        this.simpMessagingTemplate = simpMessagingTemplate;
        this.sessionService = sessionService;
        this.repo = repo;
        this.mapper = mapper;
        this.encoder = encoder;
        this.emailService = emailService;
        this.tokenService = tokenService;
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


    public boolean registerUser(RegisterUser newUser) {

        Optional<User> existing = repo.findByUsername(newUser.getUsername());

        if (existing.isEmpty()) {
            User user = mapper.map(newUser, User.class);
            user.setPassword(encoder.encode(user.getPassword()));
            user.setVerified(false);
            user.setEmailSent(false);
            user.setVerificationUrl(generateVerificationUrl(user.getEmail(), user.getUsername()));
            try {
                user = repo.save(user);

                return user.getId() > -1 && sendVerificationEmail(user);
            } catch (Exception e) {
                log.error("Registration failed ! {}", e.getMessage());

                return false;
            }

        } else {
            return false;
        }
    }

    private String generateVerificationUrl(String email, String user) {
        String token = jwtUtil.generateToken(email, 1000 * 60 * 5);

        return "/app/music/public/verify?user="+user+"&token="+token;
    }

    private boolean sendVerificationEmail(User user) {
        Map<String, Object> templateVariables = new HashMap<>();
        String verifyUrl = String.format("%s" + user.getVerificationUrl(), baseUrl);
        templateVariables.put("username", user.getUsername());
        templateVariables.put("verificationUrl", verifyUrl);
        try {
            emailService.sendTemplateEmail(user.getEmail(), "Verify Your Email - Connecting Notes", "verify", templateVariables);
            log.info("Email Sent for verification to : {}", user.getEmail());
            user.setEmailSent(true);
            repo.save(user);
            return true;
        } catch (MessagingException e) {
            log.info("Email Sent failed for verification to : {} due to {}", user.getEmail(), e.getMessage());
            return false;
        }
    }

    public String validateTokenAndUpdate(String username, String token) {
        long timestamp = System.currentTimeMillis();
        log.info("Data received for token generation  username {} ,  time {} , token {}",username,timestamp,token);

        String email = null;
        try{
            email = jwtUtil.getIdentityFromToken(token);
        } catch (Exception e) {
            log.info("failed at token extraction {}",e.getMessage());
            return tokenService.generateToken(timestamp,"failed")+"$"+username;
        }

        User user = repo.findByEmail(email);
        if (user.isVerified()) {
            log.warn("User :  {} Already Verified",user.getUsername());
            return tokenService.generateToken(timestamp,username)+"$"+username;
        }
        if (user.getUsername().equals(username)) {
            user.setVerified(true);
            repo.save(user);
            log.info("Email verified Successfully {}", email);
            return tokenService.generateToken(timestamp,username)+"$"+username;
        } else {
            log.info("Failed to verify the Email {}", email);
            return tokenService.generateToken(timestamp,"failed")+"$"+username;
        }


    }

    public boolean validateToken(String token,String username) {
        // ✅ Extract timestamp and field from token (no database needed!)
        TokenService.TokenData data = tokenService.extractData(token);

        if (data == null) {
            log.error("Invalid token");
            return false;
        }

        if(data.getGenericField().equals("failed")){
            log.info("For User : {} some error occurred , validation failed",username);
            return false;
        }

        // Find and verify user
        User user = repo.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        long originalTimestamp = data.getTimestamp();
        // Calculate time difference
        long currentTime = Timestamp.valueOf(user.getCreatedAt()).getTime();
        long seconds = TimeUnit.MILLISECONDS.toSeconds(originalTimestamp-currentTime );

        log.info("Token age: {} seconds", seconds);

        if (seconds > 299) {
            log.info("Token expired! {} seconds old", seconds);
            return false;
        }

        if(!data.getGenericField().equals("failed") &&  !data.getGenericField().equals(username)) {
            log.info("Tampered with url! actual username{}, passed username {}",data.getGenericField(),username);
            return false;
        }

        if(data.getGenericField().equals("failed") && data.getGenericField().equals(username)){
            log.info("Tampered with URLs username! actual username{}, passed username {}",data.getGenericField(),username);
            return false;
        }


        return true;
    }
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

    public String getUserEmail(String username) {
        Optional<User> user = repo.findByUsername(username);
        if (user.isEmpty()) {
            return "";
        }
        return user.get().getEmail();
    }
}
