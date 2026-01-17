package com.music.musicwebapplication.service;

import com.music.musicwebapplication.dto.RegisterUser;
import com.music.musicwebapplication.entity.User;
import com.music.musicwebapplication.repo.UserRepo;
import com.music.musicwebapplication.utils.JwtTokenUtil;
import jakarta.mail.MessagingException;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class RegisterUserService {
    @Value("${app.music.base.url}")
    private String baseUrl;

    private final UserRepo repo;
    private final JwtTokenUtil utils;
    private final ModelMapper mapper;
    private final PasswordEncoder encoder;
    private final EmailAgentService emailService;
    private final TokenService tokenService;

    @Autowired
    RegisterUserService(UserRepo repo, JwtTokenUtil utils, ModelMapper mapper, PasswordEncoder encoder, EmailAgentService emailService, TokenService tokenService) {
        this.repo = repo;
        this.utils = utils;
        this.mapper = mapper;
        this.encoder = encoder;
        this.emailService = emailService;
        this.tokenService = tokenService;
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
        String token = utils.generateToken(email, 1000 * 60 * 5);

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

    public String getUserEmail(String username) {
        Optional<User> user = repo.findByUsername(username);
        if (user.isEmpty()) {
            return "";
        }
        return user.get().getEmail();
    }

    public String validateTokenAndUpdate(String username, String token) {
        long timestamp = System.currentTimeMillis();
        log.info("Data received for token generation  username {} ,  time {} , token {}",username,timestamp,token);

        String email = null;
        try{
             email = utils.getIdentityFromToken(token);
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


}
