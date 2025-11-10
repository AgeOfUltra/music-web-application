package com.music.musicwebapplication.service;

import com.music.musicwebapplication.utils.JwtTokenUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

@Service
public class PublicLoginService {
    private final JwtTokenUtil jwtUtil;

    public PublicLoginService(JwtTokenUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    public String extractUsernameFromJwt(HttpServletRequest request) {
        String cookieHeader = request.getHeader("Cookie");
        if (cookieHeader != null) {
            for (String c : cookieHeader.split(";")) {
                String trimmed = c.trim();
                if (trimmed.startsWith("jwt=")) {
                    String token = trimmed.substring("jwt=".length());
                    return jwtUtil.getUserNameFromToken(token);
                }
            }
        }
        return null;
    }
}
