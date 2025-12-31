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

        if (token == null) {
            handleUnauthenticated(request, response);
            return;
        }

        if (shouldNotFilter(request)) {

            filterChain.doFilter(request, response);
            return;
        }


        try {
            username = jwtTokenUtil.getUserNameFromToken(token); // may throw ExpiredJwtException

            UserSession session = sessionService.getUserSessionForToken(token);

            if(session == null || session.isSessionExpired()){
                log.warn("⏱ Session expired for {}", username);
                forceLogout(session, response);
                handleUnauthenticated(request, response);
                return;
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
            handleUnauthenticated(request, response);
        }


    }
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();

        log.info(" request : {}",path);

        return path.contains("/public/")
                || path.contains("/css/")
                || path.contains("/js/")
                || path.contains("/images/")
                || path.contains("/h2-console")
                || path.contains("/favicon");
    }

    private void handleUnauthenticated(
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {

        SecurityContextHolder.clearContext();

        if ("XMLHttpRequest".equals(request.getHeader("X-Requested-With"))) {
            // ✅ AJAX requests - send JSON response for frontend to handle
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Session expired\",\"redirect\":\"/app/music/public/login?expired=true\"}");
        } else {
            // Regular page requests - redirect directly
            response.sendRedirect("/app/music/public/login?expired=true");
        }
    }
    private void forceLogout(UserSession session, HttpServletResponse response) {
        try {
            if (session != null) {
                boolean logoutSuccess = loginService.logout(session);

                if (!logoutSuccess) {
                    log.warn("⚠️ Logout cleanup partially failed for user: {}", session.getUsername());
                }
            }
        } catch (Exception e) {
            log.error("❌ Logout cleanup failed: {}", e.getMessage(), e);
        }

        // ✅ Always clear cookie regardless of logout success
        // (User should be forced to re-login even if backend cleanup failed)
        Cookie cookie = new Cookie("jwt", null);
        cookie.setMaxAge(0);
        cookie.setPath("/");
        cookie.setHttpOnly(true); // ✅ Security: match the original cookie settings
        response.addCookie(cookie);

        log.info("✅ JWT cookie cleared for expired session");
    }



}
