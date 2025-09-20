package com.music.musicwebapplication.utils.filter;

import com.music.musicwebapplication.utils.JwtTokenUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
@Component
public class JwtAuthenticationFilter  extends OncePerRequestFilter {

    private final JwtTokenUtil jwtTokenUtil;
    private final UserDetailsService service;

    @Autowired
    JwtAuthenticationFilter(JwtTokenUtil util,@Lazy UserDetailsService service){
        this.jwtTokenUtil= util;
        this.service = service;
    }
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        String token = null;
        String username = null;

        // First, try to get token from Authorization header
        if(authorization != null && authorization.startsWith("Bearer ")){
            token = authorization.substring(7);
        }

        // If no token in header, check for JWT cookie
        if(token == null) {
            jakarta.servlet.http.Cookie[] cookies = request.getCookies();
            if(cookies != null) {
                for(jakarta.servlet.http.Cookie cookie : cookies) {
                    if("jwtToken".equals(cookie.getName())) {
                        token = cookie.getValue();
                        break;
                    }
                }
            }
        }

        // Extract username from token
        if(token != null) {
            username = jwtTokenUtil.getUserNameFromToken(token);
        }

        // Authenticate user if token is valid
        if(username != null && SecurityContextHolder.getContext().getAuthentication() == null){
            try {
                UserDetails userDetails = service.loadUserByUsername(username);
                if(jwtTokenUtil.validateToken(username, userDetails.getUsername(), token)){
                    UsernamePasswordAuthenticationToken authenticationToken =
                        new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                    authenticationToken.setDetails(new WebAuthenticationDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authenticationToken);
                }
            } catch (Exception e) {
                // Invalid token or user not found - continue without authentication
                System.out.println("JWT authentication failed: " + e.getMessage());
            }
        }
        filterChain.doFilter(request, response);
    }
}
