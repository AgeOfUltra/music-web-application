package com.music.musicwebapplication.controller;

import com.music.musicwebapplication.dto.LoginUser;
import com.music.musicwebapplication.dto.RegisterUser;
import com.music.musicwebapplication.service.RegisterUserService;
import com.music.musicwebapplication.support.Role;
import com.music.musicwebapplication.utils.JwtTokenUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/music")
public class LoginController {

    private final RegisterUserService userService;

    private final AuthenticationManager manager;

    private final JwtTokenUtil jwtUtil;


    @Autowired
    LoginController(RegisterUserService service , AuthenticationManager manager, JwtTokenUtil jwtUtil){
        this.userService=service;
        this.manager= manager;
        this.jwtUtil = jwtUtil;
    }


    @PostMapping("/public/signUp")
    public ResponseEntity<String> registerUser(@RequestBody RegisterUser newUser){
        newUser.setRole(Role.LISTENER);
        String result = userService.registerUser(newUser);

        return ResponseEntity.status(
                HttpStatus.CREATED
        ).body(result);
    }

    @PostMapping("/public/authenticate")
    public ResponseEntity<String> loginUser(@RequestBody LoginUser login){

        String token;

        try{
            manager.authenticate(new UsernamePasswordAuthenticationToken(login.getUsername(),login.getPassword()));
            token=jwtUtil.generateToken(login.getUsername());
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(token);
        }catch (Exception e){
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Please enter correct Credentials");
        }
    }
}
