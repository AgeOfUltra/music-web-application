package com.music.musicwebapplication.controller;

import com.music.musicwebapplication.dto.LoginUser;
import com.music.musicwebapplication.dto.RegisterUser;
import com.music.musicwebapplication.entity.Room;
import com.music.musicwebapplication.entity.UserSession;
import com.music.musicwebapplication.exception.RoomNotFoundException;
import com.music.musicwebapplication.service.PublicLoginService;
import com.music.musicwebapplication.service.RegisterUserService;
import com.music.musicwebapplication.service.RoomService;
import com.music.musicwebapplication.service.UserSessionService;
import com.music.musicwebapplication.support.Role;
import com.music.musicwebapplication.utils.JwtTokenUtil;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.Errors;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Controller
@RequestMapping("/app/music/public")
public class PublicLoginController {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenUtil jwtTokenUtil;
    private final RoomService roomService;
    private final RegisterUserService userService;
    private final UserSessionService sessionService;
    private final JwtTokenUtil jwtUtil;
    private final SimpMessagingTemplate simpMessagingTemplate;

    public PublicLoginController(AuthenticationManager authenticationManager, JwtTokenUtil jwtTokenUtil, RoomService roomService, RegisterUserService userService, UserSessionService sessionService, PublicLoginService loginService, JwtTokenUtil jwtUtil, SimpMessagingTemplate simpMessagingTemplate) {
        this.authenticationManager = authenticationManager;
        this.jwtTokenUtil = jwtTokenUtil;
        this.roomService = roomService;
        this.userService = userService;
        this.sessionService = sessionService;
        this.jwtUtil = jwtUtil;
        this.simpMessagingTemplate = simpMessagingTemplate;
    }

    // Return login page
    @GetMapping("/login")
    public String loginPage(@RequestParam(required=false) String error,
                            @RequestParam(required=false) String logout, Model model) {
        if ("alreadyLoggedIn".equals(error)) {
            model.addAttribute("loginError", "User already logged in");
        }
        if ("sessionError".equals(error)) {
            model.addAttribute("sessionError", "Error occurred while session create/update Please try again after sometime.");
        }
        if(logout!=null && logout.equals("true")){
            model.addAttribute("loginError", "User logged out successfully");
        }

        if (!model.containsAttribute("loginUser")) {
            model.addAttribute("loginUser", new LoginUser());
        }
        return "login";
    }

    @GetMapping("/signUp")
    public String signUpPage(Model model) {
        if (!model.containsAttribute("newUser")) {
            model.addAttribute("newUser", new RegisterUser());
        }
        return "signup";
    }

    // Handle login and return JWT token
    @PostMapping("/authenticate")
    public ModelAndView loginUser(@ModelAttribute("loginUser") LoginUser loginUser, HttpServletResponse responseServlet, RedirectAttributes redirectAttributes) {
        String errorMessage = "";
        ResponseEntity<?> response = authenticate(loginUser);
        log.info(response.toString());
        Map<String, Object> responseBody = (Map<String, Object>) response.getBody();

        if (responseBody == null) {
            log.error("Unknown issue occurred in response body generation! please check the api call and relevant methods");
            redirectAttributes.addFlashAttribute("loginError", "Unknown issue occurred,Please try again");
            redirectAttributes.addFlashAttribute("loginUser", loginUser);
            return new ModelAndView("redirect:/app/music/public/login");
        }
        if (response.getStatusCode() == HttpStatus.OK) {
            String token = "";
            token = (String) responseBody.get("token");

            ResponseCookie cookie = ResponseCookie.from("jwt", token)
                    .httpOnly(true)
                    .secure(false)           // true in production (HTTPS)
                    .path("/")
                    .maxAge(60 * 60)         // 1 hour
                    .sameSite("Lax")      // or "Lax" depending on your flows
                    .build();

            responseServlet.addHeader("Set-Cookie", cookie.toString());

            log.info("Login Successfully ! logg in user data : {}", loginUser);
            return new ModelAndView("redirect:/app/music/dashboard");
        } else {
            errorMessage = (String) responseBody.get("error");
            if( errorMessage == null || errorMessage.isEmpty()){
                errorMessage=(String)responseBody.get("UserError");
            }
            redirectAttributes.addFlashAttribute("loginError", errorMessage);
            redirectAttributes.addFlashAttribute("loginUser", loginUser);
            log.info("login failed! user data : {}", loginUser);
            return new ModelAndView("redirect:/app/music/public/login");
        }
    }

    //    API
    // PublicLoginController
    public ResponseEntity<?> authenticate(LoginUser loginUser) {
        Map<String, Object> response = new HashMap<>();
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            loginUser.getUsername(), loginUser.getPassword())
            );

            UserDetails userDetails = (UserDetails) authentication.getPrincipal();

            if (roomService.isUserPresentInAnyRoom(userDetails.getUsername())) {
                response.put("UserError", "User already exist in one of the room");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response); // <— map, not String
            }

            String token = jwtTokenUtil.generateToken(userDetails.getUsername());
            String username = userDetails.getUsername();

            response.put("token", token);
            response.put("username", username);
            response.put("message", "Login successful");

            boolean isSaved = sessionService.saveSession(token, username);
            if (!isSaved) {
                response.put("error", "User already logged in");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("error", "Invalid credentials");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }
    }


    @PostMapping("/register")
    public ModelAndView registerUser(@Valid @ModelAttribute("newUser") RegisterUser newUser, Errors error, RedirectAttributes redirectAttributes) {
        if (error.hasErrors()) {
            log.error("Register validation failed due to error : {}", error);
            log.info("failed Dta ! : {}", newUser);
            redirectAttributes.addFlashAttribute("org.springframework.validation.BindingResult.newUser", error);
            redirectAttributes.addFlashAttribute("newUser", newUser);
            return new ModelAndView("redirect:/app/music/public/signUp");
        }

        ResponseEntity<?> response = registerUserApi(newUser);
        if (response.getStatusCode().equals(HttpStatus.CREATED)) {
            log.info("New User created successfully! and his/her data : {}", newUser);
            redirectAttributes.addFlashAttribute("showRegistrationSuccess", true);
            return new ModelAndView("redirect:/app/music/public/login");
        } else {
            redirectAttributes.addAttribute("signUpError", "Error while creating user.Please try again");
            redirectAttributes.addFlashAttribute("newUser", newUser);
            log.error("failed to create new user! passed data : {}", newUser);
            return new ModelAndView("redirect:/app/music/public/signUp");
        }

    }

    private ResponseEntity<String> registerUserApi(RegisterUser newUser) {
        newUser.setRole(Role.LISTENER);
        String result = userService.registerUser(newUser);
        return result.contains("Successfully") ? ResponseEntity.status(HttpStatus.CREATED).body(result) :ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result) ;
    }

    // ==================== ✅ FIXED: LOGOUT METHOD ====================
    // ==================== ✅ VERIFIED LOGOUT METHOD ====================
// This handles BOTH scenarios:
// 1. User in room → Exit room + delete session
// 2. User NOT in room → Just delete session

    @GetMapping("/logout")
    public String logout(HttpServletRequest request, HttpServletResponse response) {
        try {
            // Get JWT from cookie
            Cookie[] cookies = request.getCookies();
            String jwtToken = null;

            if (cookies != null) {
                for (Cookie cookie : cookies) {
                    if ("jwt".equals(cookie.getName())) {
                        jwtToken = cookie.getValue();
                        break;
                    }
                }
            }

            if (jwtToken != null) {
                String username = jwtUtil.getUserNameFromToken(jwtToken);

                log.info("🚪 LOGOUT: User {} logging out", username);

                // Get current session
                UserSession session = sessionService.getUserSession(username);

                if (session != null && session.getRoomName() != null) {
                    // ==================== SCENARIO 1: USER IN ROOM ====================
                    String roomName = session.getRoomName();
                    log.info("🏠 User {} is in room {}, exiting with full logout...", username, roomName);

                    try {
                        // ✅ Exit room with fullLogout=true (deletes session)
                        boolean removed = roomService.exitFromRoom(roomName, username, true);

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
                                // Room deleted (last organizer) - expected
                                log.info("ℹ️ Room {} deleted (last organizer left)", roomName);
                            }
                        }

                    } catch (Exception e) {
                        log.error("❌ Error exiting room during logout: {}", e.getMessage());

                        // ⚠️ CRITICAL: Ensure session is deleted even if room exit fails
                        try {
                            sessionService.deleteUserSession(username);
                            log.info("✅ Session deleted as fallback after room exit error");
                        } catch (Exception ex) {
                            log.error("❌ Error deleting session: {}", ex.getMessage());
                        }
                    }

                } else {
                    // ==================== SCENARIO 2: USER NOT IN ROOM ====================
                    log.info("📋 User {} not in any room, deleting session only", username);

                    try {
                        sessionService.deleteUserSession(username);
                        log.info("✅ Session deleted successfully");
                    } catch (Exception e) {
                        log.error("❌ Error deleting session: {}", e.getMessage());
                    }
                }

                log.info("✅ User {} logout complete", username);

            } else {
                log.warn("⚠️ No JWT token found during logout");
            }

            // ✅ Clear JWT cookie
            Cookie deleteCookie = new Cookie("jwt", null);
            deleteCookie.setMaxAge(0);
            deleteCookie.setPath("/");
            deleteCookie.setHttpOnly(true);
            response.addCookie(deleteCookie);

            // Clear security context
            SecurityContextHolder.clearContext();

        } catch (Exception e) {
            log.error("❌ Unexpected error during logout: {}", e.getMessage(), e);
        }

        return "redirect:/app/music/public/login?logout=true";
    }
}