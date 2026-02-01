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
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
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
        log.debug("PublicAuthService initialized");
    }

    public String extractUsernameFromJwt(HttpServletRequest request) {
        log.debug("Extracting username from JWT cookie");
        String cookieHeader = request.getHeader("Cookie");
        if (cookieHeader != null) {
            for (String c : cookieHeader.split(";")) {
                String trimmed = c.trim();
                if (trimmed.startsWith("jwt=")) {
                    String token = trimmed.substring("jwt=".length());
                    String username = jwtUtil.getIdentityFromToken(token);
                    log.debug("Username extracted from JWT: {}", username);
                    return username;
                }
            }
        }
        log.debug("No JWT cookie found in request");
        return null;
    }

    @Transactional
    public boolean registerUser(RegisterUser newUser) {
        log.info("Attempting to register new user: {}", newUser.getUsername());

        Optional<User> existing = repo.findByUsername(newUser.getUsername());

        if (existing.isEmpty()) {
            User user = mapper.map(newUser, User.class);
            user.setPassword(encoder.encode(user.getPassword()));
            user.setVerified(false);
            user.setEmailSent(false);
            user.setVerificationUrl(generateVerificationUrl(user.getEmail(), user.getUsername()));
            log.debug("User entity created with verification URL for: {}", newUser.getUsername());

            try {
                user = saveUserInDbWithRetry(user);
                log.debug("User saved to database with id: {}", user.getId());

                boolean emailSent = sendVerificationEmail(user);
                if(emailSent){
                    log.info("User registered successfully: {}", newUser.getUsername());
                } else {
                    log.warn("User registered but verification email failed for: {}", newUser.getUsername());
                }
                return user.getId() > -1 && emailSent;
            } catch (Exception e) {
                log.error("Registration failed for user {}: {}", newUser.getUsername(), e.getMessage(), e);
                return false;
            }

        } else {
            log.warn("Registration failed - username already exists: {}", newUser.getUsername());
            return false;
        }
    }

    @Retryable(
            retryFor = {SocketException.class, SocketTimeoutException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public User saveUserInDbWithRetry(User u){
        try{
            return repo.save(u);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    private String generateVerificationUrl(String email, String user) {
        log.debug("Generating verification URL for user: {}", user);
        String token = jwtUtil.generateToken(email, 1000 * 60 * 5);

        return "/app/music/public/verify?user="+user+"&token="+token;
    }

    private boolean sendVerificationEmail(User user) {
//        log.info("Sending verification email to: {}", user.getEmail());
        Map<String, Object> templateVariables = new HashMap<>();
        String verifyUrl = String.format("%s" + user.getVerificationUrl(), baseUrl);
        templateVariables.put("username", user.getUsername());
        templateVariables.put("verificationUrl", verifyUrl);

        try {
            emailService.sendTemplateEmail(user.getEmail(), "Verify Your Email - Connecting Notes", "verify", templateVariables);
            log.info("Verification email sent successfully to: {}", user.getEmail());
            user.setEmailSent(true);
            saveUserInDbWithRetry(user);
            return true;
        } catch (MessagingException e) {
            log.error("Failed to send verification email to {}: {}", user.getEmail(), e.getMessage(), e);
            return false;
        }
    }

    public String validateTokenAndUpdate(String username, String token) {
        log.info("Validating token and updating user: {}", username);
        long timestamp = System.currentTimeMillis();
        log.debug("Token validation started at timestamp: {}", timestamp);

        String email = null;
        try{
            email = jwtUtil.getIdentityFromToken(token);
            log.debug("Email extracted from token: {}", email);
        } catch (Exception e) {
            log.error("Failed to extract email from token for user {}: {}", username, e.getMessage());
            return tokenService.generateToken(timestamp,"failed")+"$"+username;
        }

        User user = repo.findByEmail(email);
        if (user == null) {
            log.warn("No user found with email: {}", email);
            return tokenService.generateToken(timestamp,"failed")+"$"+username;
        }

        if (user.isVerified()) {
            log.info("User already verified: {}", user.getUsername());
            return tokenService.generateToken(timestamp,username)+"$"+username;
        }

        if (user.getUsername().equals(username)) {
            user.setVerified(true);
            saveUserInDbWithRetry(user);
            log.info("Email verified successfully for user: {}", username);
            return tokenService.generateToken(timestamp,username)+"$"+username;
        } else {
            log.warn("Username mismatch during verification - expected: {}, got: {}", user.getUsername(), username);
            return tokenService.generateToken(timestamp,"failed")+"$"+username;
        }


    }

    public boolean validateToken(String token,String username) {
        log.info("Validating token for user: {}", username);
        // Extract timestamp and field from token (no database needed!)
        TokenService.TokenData data = tokenService.extractData(token);

        if (data == null) {
            log.error("Invalid token provided for user: {}", username);
            return false;
        }

        if(data.getGenericField().equals("failed")){
            log.warn("Token validation failed for user: {}", username);
            return false;
        }

        // Find and verify user
        User user = repo.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        long originalTimestamp = data.getTimestamp();
        // Calculate time difference
        long currentTime = Timestamp.valueOf(user.getCreatedAt()).getTime();
        long seconds = TimeUnit.MILLISECONDS.toSeconds(originalTimestamp-currentTime );

        log.debug("Token age: {} seconds for user: {}", seconds, username);

        if (seconds > 299) {
            log.warn("Token expired - {} seconds old for user: {}", seconds, username);
            return false;
        }

        if(!data.getGenericField().equals("failed") &&  !data.getGenericField().equals(username)) {
            log.warn("Token tampering detected - username mismatch for user: {}", username);
            return false;
        }

        if(data.getGenericField().equals("failed") && data.getGenericField().equals(username)){
            log.warn("Token tampering detected - invalid failed state for user: {}", username);
            return false;
        }

        log.info("Token validated successfully for user: {}", username);
        return true;
    }

    public ResponseEntity<?> authenticate(LoginUser loginUser) {
        log.info("Authenticating user: {}", loginUser.getUsername());
        Map<String, Object> response = new HashMap<>();

        try {
            Optional<User> currentUse = repo.findByUsername(loginUser.getUsername());

//            Case 1 : User not registered.
            if(currentUse.isEmpty()){
                log.warn("Authentication failed - user not found: {}", loginUser.getUsername());
                response.put("UserError", "Try gain After SingUp");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }

//            Case 2 : User registered but not verified
            if(!currentUse.get().isVerified()){
                log.warn("Authentication failed - user not verified: {}", loginUser.getUsername());
                response.put("UserError", "Kindly Validate the your account");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
            }

//Case 3 : User registered and Verified, if already logged in
            Optional<UserSession> loggedUser = Optional.ofNullable(sessionService.getUserSession(loginUser.getUsername()));

            if (loggedUser.isPresent()) {
                log.warn("Authentication failed - user already logged in: {}", loginUser.getUsername());
                response.put("UserError", "User already logged In!");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response); // <— map, not String
            }


//            successful Case : User Registered, Verified, and First time logging
            log.debug("Attempting Spring Security authentication for user: {}", loginUser.getUsername());
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            loginUser.getUsername(), loginUser.getPassword())
            );

            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            String token = jwtUtil.generateToken(userDetails.getUsername());
            String username = userDetails.getUsername();
            log.debug("JWT token generated for user: {}", username);

            response.put("token", token);
            response.put("username", username);


            boolean isSaved = sessionService.saveSession(token, username);
            if (!isSaved) {
                log.error("Failed to save session for user: {}", username);
                response.put("error", "Unexpected Error! Try again after Some time");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
            }

            log.debug("User authenticated successfully: {}", username);
            response.put("message", "Login successful");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Authentication failed for user {}: {}", loginUser.getUsername(), e.getMessage());
            response.put("error", "Invalid credentials");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }
    }

    public boolean logout(UserSession session) {
        if (session == null) {
            log.error("Cannot logout: session is null");
            return false;
        }

        boolean success = true;
        String username = session.getUsername();
        String roomName = session.getRoomName();

        log.info("Logout initiated for user: {}", username);

        try {
            if (roomName != null ) {
                // ==================== SCENARIO 1: USER IN ROOM ====================
                log.debug("User {} is in room {}, exiting with {} logout", username, roomName, session.isIntentionalLogout() ? "full" : "partial");

                try {
                    // Exit room with fullLogout=true (deletes session)
                    boolean removed = roomService.exitFromRoom(roomName, username, session.isIntentionalLogout());

                    if (removed) {
                        log.info("User {} exited room {} successfully (session deleted)", username, roomName);

                        // Broadcast to remaining participants
                        try {
                            Room updatedRoom = roomService.getRoomDetails(roomName);

                            simpMessagingTemplate.convertAndSend(
                                    "/topic/chat/" + roomName + "/participants",
                                    updatedRoom.getParticipant()
                            );

                            log.debug("Broadcasted participant update for room: {}", roomName);

                        } catch (RoomNotFoundException e) {
                            // Room deleted (last organizer) - this is expected behavior
                            log.info("Room {} deleted (last organizer left)", roomName);
                        } catch (Exception e) {
                            log.error("Error broadcasting participant update for room {}: {}", roomName, e.getMessage());
                            success = false; // Non-critical error, but mark as partial failure
                        }
                    } else {
                        log.error("Failed to remove user {} from room {}", username, roomName);
                        success = false;
                    }

                } catch (Exception e) {
                    log.error("Error exiting room during logout for user {}: {}", username, e.getMessage(), e);
                    success = false;

                    // CRITICAL: Ensure session is deleted even if room exit fails
                    try {
                        sessionService.deleteUserSession(username);
                        log.info("Session deleted as fallback after room exit error for user: {}", username);
                    } catch (Exception ex) {
                        log.error("CRITICAL: Failed to delete session as fallback for user {}: {}", username, ex.getMessage(), ex);
                        return false; // Total failure
                    }
                }

            } else {
                // ==================== SCENARIO 2: USER NOT IN ROOM ====================
                log.info("User {} not in any room, deleting session only", username);

                try {
                    sessionService.deleteUserSession(username);
                    log.debug("Session deleted successfully for user: {}", username);
                } catch (Exception e) {
                    log.error("Error deleting session for user {}: {}", username, e.getMessage(), e);
                    return false; // Critical failure - session not deleted
                }
            }

            if (success) {
                log.info("Logout completed successfully for user: {}", username);
            } else {
                log.warn("Logout completed with errors for user: {}", username);
            }

            return success;

        } catch (Exception e) {
            log.error("Unexpected error during logout for user {}: {}", username, e.getMessage(), e);

            // Last-ditch effort to clean up session
            try {
                sessionService.deleteUserSession(username);
                log.info("Session deleted in final error recovery for user: {}", username);
                return false; // Cleanup done but there were errors
            } catch (Exception ex) {
                log.error("CRITICAL: Failed to delete session in final error recovery for user {}: {}", username, ex.getMessage(), ex);
                return false; // Total failure
            }
        }
    }

    public String getUserEmail(String username) {
        log.debug("Fetching email for user: {}", username);
        Optional<User> user = repo.findByUsername(username);
        if (user.isEmpty()) {
            log.warn("No user found with username: {}", username);
            return "";
        }
        log.debug("Email retrieved for user: {}", username);
        return user.get().getEmail();
    }

    public Optional<List<User>> getOlderThan5MinUsers() {
        LocalDateTime cutoffTime = LocalDateTime.now().minusMinutes(5);
        return repo.findRecordsOlderThan5Minutes(cutoffTime);
    }
    @Retryable(
            retryFor = {SocketException.class, SocketTimeoutException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public void deleteWithRetry(User u){
        try{
            repo.delete(u);
//            log.info("Deleted {}",u.getEmail());
        }catch (Exception e){
            log.debug("failed to delete user because : {}",e.getMessage());
        }
    }
}
