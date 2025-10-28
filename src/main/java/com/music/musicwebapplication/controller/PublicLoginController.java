package com.music.musicwebapplication.controller;

import com.music.musicwebapplication.dto.LoginUser;
import com.music.musicwebapplication.dto.RegisterUser;
import com.music.musicwebapplication.exception.RoomManageException;
import com.music.musicwebapplication.service.RegisterUserService;
import com.music.musicwebapplication.service.RoomService;
import com.music.musicwebapplication.support.Role;
import com.music.musicwebapplication.utils.JwtTokenUtil;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;

import java.util.HashMap;
import java.util.Map;

@Controller
@RequestMapping("/app/music/public")
@RequiredArgsConstructor
public class PublicLoginController {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenUtil jwtTokenUtil;
    private final RoomService roomService;
    private final RegisterUserService userService;

    // Return login page
    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }

    // Handle login and return JWT token
    @PostMapping("/authenticate")
    public ModelAndView loginUser(@ModelAttribute LoginUser loginUser, HttpServletResponse responseServlet, HttpSession session) {

        ResponseEntity<?> response = authenticate(loginUser);
        if(response.getStatusCode()==HttpStatus.OK){
            Map<String, Object> responseBody = (Map<String, Object>) response.getBody();
            String token = (String) responseBody.get("token");

            // Store in session (server-side)
            session.setAttribute("jwtToken", token);
            session.setAttribute("username", loginUser.getUsername());

            //store the token in cookies for client side
            Cookie cookie = new Cookie("jwtToken",token);
            cookie.setHttpOnly(true);
            cookie.setSecure(true);
            cookie.setPath("/");
            cookie.setMaxAge(3600);
            cookie.setAttribute("username",loginUser.getUsername());
            responseServlet.addCookie(cookie);
            return new ModelAndView( "redirect:/app/music/dashboard");

        }else{
            return new ModelAndView("redirect:/app/music/public/login");
        }
    }

//    API
    public ResponseEntity<?> authenticate( LoginUser loginUser) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            loginUser.getUsername(),
                            loginUser.getPassword()
                    )
            );

            UserDetails userDetails = (UserDetails) authentication.getPrincipal();

            if(roomService.isUserPresentInAnyRoom(userDetails.getUsername())){
                throw new RoomManageException("User already exist in one of the room");
            }
            String token = jwtTokenUtil.generateToken(userDetails.getUsername());

            Map<String, Object> response = new HashMap<>();
            response.put("token", token);
            response.put("username", userDetails.getUsername());
            response.put("message", "Login successful");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Invalid credentials");
            return ResponseEntity.badRequest().body(error);
        }
    }
    @PostMapping("/register")
    public ModelAndView registerUser(@ModelAttribute RegisterUser newUser, Model model){
        ResponseEntity<?> response = registerUserApi(newUser);
        if(response.getStatusCode().equals(HttpStatus.CREATED)){
            model.addAttribute("success","User created successfully");
            return new ModelAndView("redirect:/app/music/public/login");
        }else{
            model.addAttribute("error","Error while creating user.");
            return new ModelAndView("redirect:app/music/public/register");
        }

    }

    private ResponseEntity<String> registerUserApi(RegisterUser newUser){
        newUser.setRole(Role.LISTENER);
        String result = userService.registerUser(newUser);

        return ResponseEntity.status(
                HttpStatus.CREATED
        ).body(result);
    }
}