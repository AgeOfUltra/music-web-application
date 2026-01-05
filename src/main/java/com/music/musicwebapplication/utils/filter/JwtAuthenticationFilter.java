package com.music.musicwebapplication.utils.filter;

import com.music.musicwebapplication.entity.UserSession;
import com.music.musicwebapplication.service.PublicLoginService;
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

            SecurityContextHolder.clearContext();

            try {
                sessionService.deleteUserSessionByToken(token);
            } catch (Exception ex) {
                log.error("Session update failed", ex);
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
        if (request.getDispatcherType() != DispatcherType.REQUEST) {
            return true;
        }
        String path = request.getRequestURI();

        List<String> ignore = Arrays.asList("public", "css", "js", "images", "h2-console", "favicon");

        boolean isIgnored = ignore.stream().anyMatch(path::contains);
        log.info("requested path {} is ignored {}", path, isIgnored || path.equals("/"));

        return path.equals("/") || isIgnored;
    }
}