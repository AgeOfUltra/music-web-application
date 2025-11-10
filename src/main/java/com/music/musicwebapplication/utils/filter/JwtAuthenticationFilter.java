package com.music.musicwebapplication.utils.filter;

import com.music.musicwebapplication.exception.RoomManageException;
import com.music.musicwebapplication.repo.UserRepo;
import com.music.musicwebapplication.service.RoomService;
import com.music.musicwebapplication.service.UserSessionService;
import com.music.musicwebapplication.utils.JwtTokenUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
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
import java.util.Map;
import java.util.Optional;

@Component
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenUtil jwtTokenUtil;
    private final UserDetailsService service;

    private final UserSessionService sessionService;

    @Autowired
    JwtAuthenticationFilter(JwtTokenUtil util, @Lazy UserDetailsService service, UserSessionService sessionService) {
        this.jwtTokenUtil = util;
        this.service = service;
        this.sessionService = sessionService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

        String username = null;
        String token = null;

        if (request.getCookies() != null) {
            for (var c : request.getCookies()) {
                if ("jwt".equals(c.getName())) {
                    token = c.getValue();
                    break;
                }
            }
        }


        if (token != null) {
            username = jwtTokenUtil.getUserNameFromToken(token);
        }



        if (username != null  && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                UserDetails userDetails = service.loadUserByUsername(username);
                if (jwtTokenUtil.validateToken(username, userDetails.getUsername(), token)) {
                    UsernamePasswordAuthenticationToken authenticationToken =
                            new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                    authenticationToken.setDetails(new WebAuthenticationDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authenticationToken);
                }
            } catch (Exception e) {
                logger.error("JWT authentication failed: " + e.getMessage());
            }
        }
        filterChain.doFilter(request, response);
    }
}
