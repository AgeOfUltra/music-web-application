package com.music.musicwebapplication.controller;

import com.music.musicwebapplication.dto.LoginUser;
import com.music.musicwebapplication.dto.RegisterUser;
import com.music.musicwebapplication.entity.UserSession;
import com.music.musicwebapplication.enums.Role;
import com.music.musicwebapplication.service.PublicAuthService;
import com.music.musicwebapplication.service.UserSessionService;
import com.music.musicwebapplication.utils.JwtTokenUtil;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.propertyeditors.StringTrimmerEditor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.Errors;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;
import java.util.Optional;

@Slf4j
@Controller
@RequestMapping("/app/music/public")
public class PublicAuthController {


    private final UserSessionService sessionService;
    private final JwtTokenUtil jwtUtil;
    private final PublicAuthService loginService;

    public PublicAuthController(UserSessionService sessionService, JwtTokenUtil jwtUtil, PublicAuthService loginService1) {


        this.sessionService = sessionService;
        this.jwtUtil = jwtUtil;
        this.loginService = loginService1;
    }

    // Return login page
    @GetMapping("/login")
    public String loginPage(@RequestParam(required = false) String error, @RequestParam(required = false) String logout, @RequestParam(required = false) String expired, Model model) {
        if ("alreadyLoggedIn".equals(error)) {
            model.addAttribute("loginError", "User already logged in");
        }
        if ("sessionError".equals(error)) {
            model.addAttribute("sessionError", "Error occurred while session create/update Please try again after sometime.");
        }
        if (logout != null && logout.equals("true")) {
            model.addAttribute("loginError", "User logged out successfully");
        }
        if ("true".equals(expired)) {
            model.addAttribute("loginError", "Session expired. Please login again.");
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

    @InitBinder
    public void initBinder(WebDataBinder binder) {
        binder.registerCustomEditor(String.class, "username", new StringTrimmerEditor(true));
        binder.registerCustomEditor(String.class, "email", new StringTrimmerEditor(true));
    }

    // Handle login and return JWT token
    @PostMapping("/authenticate")
    public ModelAndView loginUser(@ModelAttribute("loginUser") LoginUser loginUser, HttpServletResponse responseServlet, RedirectAttributes redirectAttributes) {
        String errorMessage = "";
        ResponseEntity<?> response = loginService.authenticate(loginUser);
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

            ResponseCookie cookie = ResponseCookie.from("jwt", token).httpOnly(true).secure(false).path("/").maxAge(60 * 62)         // 1 hour
                    .sameSite("Lax").build();

            responseServlet.addHeader("Set-Cookie", cookie.toString());

            log.info("Login Successfully ! log in user data : {}", loginUser);
            return new ModelAndView("redirect:/app/music/dashboard");
        } else {
            errorMessage = (String) responseBody.get("error");
            if (errorMessage == null || errorMessage.isEmpty()) {
                errorMessage = (String) responseBody.get("UserError");
            }
            redirectAttributes.addFlashAttribute("loginError", errorMessage);
            redirectAttributes.addFlashAttribute("loginUser", loginUser);
            log.info("login failed! user data : {}", loginUser);
            return new ModelAndView("redirect:/app/music/public/login");
        }
    }

    //    API
    // PublicLoginController

    @PostMapping("/register")
    public ModelAndView registerUser(@Valid @ModelAttribute("newUser") RegisterUser newUser, Errors error, RedirectAttributes redirectAttributes) {
        if (error.hasErrors()) {
            log.error("Register validation failed due to error : {}", error);
            log.info("failed Dta ! : {}", newUser);
            redirectAttributes.addFlashAttribute("org.springframework.validation.BindingResult.newUser", error);
            redirectAttributes.addFlashAttribute("newUser", newUser);
            return new ModelAndView("redirect:/app/music/public/signUp");
        }

        String username = newUser.getUsername();
//        validate if the username is having other than "@ $ & #" as special characters.
//        boolean allowedChar = username.chars().filter(c -> !Character.isLetterOrDigit(c)).anyMatch(c -> "@#$&".indexOf(c)==-1);
        if (!username.matches("^[a-zA-Z0-9@#$&]*$")) {
            redirectAttributes.addFlashAttribute("signUpError", "Only '@$&#' as special Character are allowed ");
            redirectAttributes.addFlashAttribute("newUser", newUser);
            log.error("User Entered Data! passed data : {}", newUser);
            return new ModelAndView("redirect:/app/music/public/signUp");
        }


        Optional<UserSession> existingUser = Optional.ofNullable(sessionService.getUserSession(newUser.getUsername()));
        if (existingUser.isPresent()) {
            redirectAttributes.addFlashAttribute("signUpError", "User Already Registered!");
            redirectAttributes.addFlashAttribute("newUser", newUser);
            log.error("User AlreadyRegistered! passed data : {}", newUser);
            return new ModelAndView("redirect:/app/music/public/signUp");
        }

        newUser.setRole(Role.LISTENER);
        boolean result = loginService.registerUser(newUser);
        if (result) {
            log.info("New User created successfully! and his/her data : {}", newUser);
            redirectAttributes.addFlashAttribute("showRegistrationSuccess", true);
            return new ModelAndView("redirect:/app/music/public/login");
        } else {
            redirectAttributes.addFlashAttribute("signUpError", "Error while creating user.Please try again");
            redirectAttributes.addFlashAttribute("newUser", newUser);
            log.error("failed to create new user! passed data : {}", newUser);
            return new ModelAndView("redirect:/app/music/public/signUp");
        }

    }

    @GetMapping("/verify")
    public ModelAndView VerifyUserEmail(@RequestParam("user") String username, @RequestParam("token") String token) {

        String result = loginService.validateTokenAndUpdate(username, token);
        //result format = token$username
        return new ModelAndView("redirect:/app/music/public/verification-success?token=" + result);

    }

    @GetMapping("/verification-success")
    public ModelAndView verificationSuccess(@RequestParam("token") String token) {
        String[] parts = token.split("\\$", 2);
        token = parts[0];
        String username=parts[1];
        log.info("Received username {}",username);
        boolean result = loginService.validateToken(token,username);

        ModelAndView mav = new ModelAndView("verification-result");
        mav.addObject("success", result);
        return mav;
    }


    @GetMapping("/logout")
    public String logoutHttp(HttpServletRequest request, HttpServletResponse response) {
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
                String username = jwtUtil.getIdentityFromToken(jwtToken);
                log.info("🔓 Processing logout for user: {}", username);

                try {
                    // Get current session
                    UserSession session = sessionService.getUserSession(username);

                    if (session != null) {
                        // Perform logout cleanup
                        boolean logoutSuccess = loginService.logout(session);

                        if (!logoutSuccess) {
                            log.warn("⚠️ Logout cleanup partially failed for user: {}", username);
                            // Continue anyway to clear cookie and security context
                        }
                    } else {
                        log.warn("⚠️ No active session found for user: {}", username);
                    }

                } catch (Exception e) {
                    log.error("❌ Error during logout cleanup for user {}: {}", username, e.getMessage(), e);
                    // Continue to clear cookie and security context even if cleanup fails
                }
            } else {
                log.warn("⚠️ No JWT token found during logout");
            }

            // ✅ Always clear JWT cookie (even if logout cleanup failed)
            Cookie deleteCookie = new Cookie("jwt", null);
            deleteCookie.setMaxAge(0);
            deleteCookie.setPath("/");
            deleteCookie.setHttpOnly(true);
            response.addCookie(deleteCookie);
            log.info("✅ JWT cookie cleared");

            // Clear security context
            SecurityContextHolder.clearContext();
            log.info("✅ Security context cleared");

        } catch (Exception e) {
            log.error("❌ Unexpected error during HTTP logout: {}", e.getMessage(), e);
            // Still try to clear cookie as fallback
            try {
                Cookie deleteCookie = new Cookie("jwt", null);
                deleteCookie.setMaxAge(0);
                deleteCookie.setPath("/");
                deleteCookie.setHttpOnly(true);
                response.addCookie(deleteCookie);
            } catch (Exception cookieEx) {
                log.error("❌ Failed to clear cookie during error recovery: {}", cookieEx.getMessage());
            }
        }

        return "redirect:/app/music/public/login?logout=true";
    }
}