package com.music.musicwebapplication.config;

import com.music.musicwebapplication.utils.JwtTokenUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * ✅ CRITICAL FIX: Removed DISCONNECT handling from ChatConfiguration
 *
 * DISCONNECT events are now handled by WebSocketDisconnectListener which listens
 * to Spring's SessionDisconnectEvent. This ensures the event fires ONCE per session,
 * not once per subscription.
 *
 * This configuration now only handles:
 * - CONNECT: Authentication
 * - SUBSCRIBE: Room tracking
 */
@Configuration
@EnableWebSocketMessageBroker
@Order(Ordered.HIGHEST_PRECEDENCE + 99)
@RequiredArgsConstructor
@Slf4j
public class ChatConfiguration implements WebSocketMessageBrokerConfigurer {

    private final JwtTokenUtil jwtTokenUtil;
    private final UserDetailsService userDetailsService;
    // ✅ REMOVED: ApplicationEventPublisher (no longer needed here)

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app/music");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/app/music/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

                if (accessor == null) {
                    return message;
                }

                // ✅ Handle CONNECT - Authentication
                if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                    try {
                        String authToken = accessor.getFirstNativeHeader("Authorization");

                        if (authToken == null || !authToken.startsWith("Bearer ")) {
                            log.warn("Missing Authorization header in WebSocket CONNECT");
                            return message;
                        }

                        String jwt = authToken.substring(7);
                        String username = jwtTokenUtil.getUserNameFromToken(jwt);

                        if (username != null && jwtTokenUtil.validateToken(username, username, jwt)) {

                            UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                            UsernamePasswordAuthenticationToken authentication =
                                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

                            SecurityContextHolder.getContext().setAuthentication(authentication);
                            accessor.setUser(authentication);

                            if (accessor.getSessionAttributes() == null) {
                                accessor.setSessionAttributes(new java.util.HashMap<>());
                            }

                            accessor.getSessionAttributes().put("username", username);

                            log.info("✅ WebSocket CONNECT authenticated as {}", username);

                        } else {
                            log.warn("JWT validation failed for username {}", username);
                        }

                    } catch (Exception e) {
                        log.error("Error in WebSocket CONNECT: {}", e.getMessage());
                    }
                }

                // ✅ Handle SUBSCRIBE - Track room subscriptions
                if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
                    try {
                        String destination = accessor.getDestination();
                        if (destination != null && destination.startsWith("/topic/chat/")) {
                            String roomId = extractRoomIdFromDestination(destination);
                            if (roomId != null && accessor.getSessionAttributes() != null) {
                                accessor.getSessionAttributes().put("roomId", roomId);

                                Object username = accessor.getSessionAttributes().get("username");
                                log.info("User {} subscribed to room: {}", username, roomId);
                            }
                        }
                    } catch (Exception e) {
                        log.error("Error processing SUBSCRIBE: {}", e.getMessage());
                    }
                }

                // ✅ CRITICAL FIX: DISCONNECT handling REMOVED
                // Now handled by WebSocketDisconnectListener using SessionDisconnectEvent
                // which fires ONCE per session instead of once per subscription

                return message;
            }
        });
    }

    private String extractRoomIdFromDestination(String destination) {
        String prefix = "/topic/chat/";
        if (destination.startsWith(prefix)) {
            String roomId = destination.substring(prefix.length());
            // Handle /playback and /participants suffixes
            if (roomId.endsWith("/playback")) {
                roomId = roomId.substring(0, roomId.length() - 9);
            } else if (roomId.endsWith("/participants")) {
                roomId = roomId.substring(0, roomId.length() - 13);
            }
            return roomId;
        }
        return null;
    }
}