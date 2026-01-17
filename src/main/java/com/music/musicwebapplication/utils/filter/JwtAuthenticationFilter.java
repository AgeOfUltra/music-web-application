package com.music.musicwebapplication.utils.filter;

import com.music.musicwebapplication.entity.UserSession;
import com.music.musicwebapplication.service.PublicAuthService;
import com.music.musicwebapplication.service.UserSessionService;
import com.music.musicwebapplication.utils.JwtTokenUtil;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.DispatcherType;
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
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenUtil jwtTokenUtil;
    private final UserDetailsService service;
    private final UserSessionService sessionService;

    @Autowired
    @Lazy private PublicAuthService loginService;


    public JwtAuthenticationFilter(JwtTokenUtil util,
                                   UserDetailsService service,
                                   UserSessionService sessionService) {
        this.jwtTokenUtil = util;
        this.service = service;
        this.sessionService = sessionService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String username;
        String token = null;

        if (request.getCookies() != null) {
            for (var c : request.getCookies()) {
                if ("jwt".equals(c.getName())) {
                    token = c.getValue();
                    break;
                }
            }
        }

        UserSession session = sessionService.getUserSessionForToken(token);

        if (token == null || session == null) {
            handleUnauthenticated(request, response);
            return;
        }


        try {
            username = jwtTokenUtil.getIdentityFromToken(token); // may throw ExpiredJwtException

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
            forceLogout(session, response);
            handleUnauthenticated(request, response);
        }
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
                // Perform logout cleanup
                boolean logoutSuccess = loginService.logout(session);

                if (!logoutSuccess) {
                    log.warn("⚠️ Logout cleanup partially failed for user: {}", session.getUsername());
                    // Continue anyway to clear cookie and security context
                }
            } else {
                log.warn("⚠️ No active session found for unknown user!");
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

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (request.getDispatcherType() != DispatcherType.REQUEST) {
            return true;
        }
        String path = request.getRequestURI();

        List<String> ignore = Arrays.asList("public", "css", "js", "images", "h2-console", "favicon","nodes");

        boolean isIgnored = ignore.stream().anyMatch(path::contains);
        log.debug("requested path {} is ignored {}", path, isIgnored || path.equals("/"));

        return path.equals("/") || isIgnored;
    }

}