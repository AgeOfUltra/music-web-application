

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
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.Errors;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;

import java.util.HashMap;
import java.util.Map;

@Slf4j
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
    public String loginPage(Model model) {
        model.addAttribute("loginUser", new LoginUser());
        return "login";
    }

    @GetMapping("/signUp")
    public String signUpPage(Model model) {
        model.addAttribute("newUser", new RegisterUser());
        return "signup";
    }

    // Handle login and return JWT token
    @PostMapping("/authenticate")
    public ModelAndView loginUser(@Valid @ModelAttribute("loginUser") LoginUser loginUser, HttpServletResponse responseServlet, HttpSession session, Errors error, Model model) {
        if(error.hasErrors()){
            log.error("Login validation failed due to error : {}", error);
            log.info("Register validation failed due to error : {}", error);
            log.info("new user data : {}", loginUser);
            return new ModelAndView("login");
        }

        String errorMessage="";
        ResponseEntity<?> response = authenticate(loginUser);
        log.info(response.toString());
        if(response.getStatusCode()==HttpStatus.OK){
            Map<String, Object> responseBody = (Map<String, Object>) response.getBody();
            String token = "";
            String message ="";

            if(responseBody!=null){
                token = (String) responseBody.get("token");
                message=(String)responseBody.get("message");
                errorMessage= (String)responseBody.get("error");
            }


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

            model.addAttribute("loginSuccess",message);
            model.addAttribute("loginError","");
            log.info("new user data : {}", loginUser);
            return new ModelAndView("redirect:/app/music/dashboard");
        }else{
            model.addAttribute("loginError",errorMessage);
            log.info("new user data : {}", loginUser);
            return new ModelAndView("login");
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
    public String registerUser(@Valid  @ModelAttribute("newUser") RegisterUser newUser, Model model,Errors error){
        if(error.hasErrors()){
            log.error("Register validation failed due to error : {}", error);
            log.info("Register validation failed due to error : {}", error);
            log.info("new user data : {}", newUser);
            return "signup";
        }

        ResponseEntity<?> response = registerUserApi(newUser);
        if(response.getStatusCode().equals(HttpStatus.CREATED)){
            model.addAttribute("success","User created successfully");
            log.info("new user data : {}", newUser);
            return "redirect:/app/music/public/login";
        }else{
            model.addAttribute("error","Error while creating user.");
            log.info("new user data : {}", newUser);
            return "signup";
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