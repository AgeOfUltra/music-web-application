package com.music.musicwebapplication.utils.filter;

import com.music.musicwebapplication.entity.UserSession;
import com.music.musicwebapplication.service.PublicLoginService;
import com.music.musicwebapplication.service.UserSessionService;
import com.music.musicwebapplication.utils.JwtTokenUtil;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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

@Component
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenUtil jwtTokenUtil;
    private final UserDetailsService service;
    private final UserSessionService sessionService;
    private final PublicLoginService loginService;


    @Autowired
    JwtAuthenticationFilter(JwtTokenUtil util, @Lazy UserDetailsService service, UserSessionService sessionService,@Lazy PublicLoginService loginService) {
        this.jwtTokenUtil = util;
        this.service = service;
        this.sessionService = sessionService;
        this.loginService = loginService;
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


        if (shouldNotFilter(request)) {

            filterChain.doFilter(request, response);
            return;
        }


        try {
            if (token != null) {
                username = jwtTokenUtil.getUserNameFromToken(token); // may throw ExpiredJwtException
            }

            if (username != null &&
                    SecurityContextHolder.getContext().getAuthentication() == null) {

                UserDetails userDetails = service.loadUserByUsername(username);

                if (jwtTokenUtil.validateToken(username, userDetails.getUsername(), token)) {
                    UsernamePasswordAuthenticationToken authenticationToken =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails,
                                    null,
                                    userDetails.getAuthorities()
                            );
                    authenticationToken.setDetails(new WebAuthenticationDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authenticationToken);
                }
            }

            filterChain.doFilter(request, response);

        } catch (ExpiredJwtException e) {
            logger.warn("JWT expired, redirecting to login");

            SecurityContextHolder.clearContext();

            log.info("Is session is still passing! {}",token);
            try{
                sessionService.updateUsesSessionExpiry(token);
            }catch (Exception a){
                log.error("Error Occurred while updating session entity! {}",a.getMessage());
            }
            try {
                // Get current session
                UserSession session = sessionService.getUserSessionForToken(token);

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

            } catch (Exception a) {
                log.error("❌ Error during logout cleanup for user {}: {}", username, e.getMessage(), a);
                // Continue to clear cookie and security context even if cleanup fails
            }

            Cookie cookie = new Cookie("jwt", null);
            cookie.setMaxAge(0);
            cookie.setPath("/");
            response.addCookie(cookie);

            response.sendRedirect("/app/music/public/login?expired=true");
            return;
        }


    }
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();

        log.info(" request : {}",path);

        return path.startsWith("/app/music/public/")
                || path.contains("/css/")
                || path.contains("/js/")
                || path.contains("/images/")
                || path.contains("/favicon");
    }

}
