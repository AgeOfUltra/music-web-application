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

        String authorization = request.getHeader("Authorization");
        String token = null;
        String username = null;


        if (authorization != null && authorization.startsWith("Bearer ")) {
            token = authorization.substring(7);
        }


        if (token == null) {
            jakarta.servlet.http.Cookie[] cookies = request.getCookies();
            if (cookies != null) {
                for (jakarta.servlet.http.Cookie cookie : cookies) {
                    if ("jwtToken".equals(cookie.getName())) {
                        token = cookie.getValue();
                        break;
                    }
                }
            }
        }

        if (token != null) {
            username = jwtTokenUtil.getUserNameFromToken(token);
        }


//        Optional<Map<String, ?>> responseSession = sessionService.updateDashBoardEntry(username, "ACTION_FROM_JWT");
//
//        if (responseSession.isEmpty()) {
//            log.error("response ie empty for session of current user");
//            response.sendRedirect("/app/music/public/login?error=alreadyLoggedIn");
//            return;
//        } else {
//            if (!responseSession.get().containsKey("ERROR") && (request.getRequestURI().contains("authenticate"))) {
//                if (responseSession.get().containsKey("ALREADY_VISITED")) {
//                    log.info("User already visited the page.");
//                    response.sendRedirect("/app/music/public/login?error=alreadyLoggedIn");
//                    return;
//                }
//            }
//        }


        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
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
