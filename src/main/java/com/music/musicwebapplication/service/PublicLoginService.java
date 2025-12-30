package com.music.musicwebapplication.service;

import com.music.musicwebapplication.entity.Room;
import com.music.musicwebapplication.entity.UserSession;
import com.music.musicwebapplication.exception.RoomNotFoundException;
import com.music.musicwebapplication.utils.JwtTokenUtil;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class PublicLoginService {
    private final JwtTokenUtil jwtUtil;
    private final RoomService roomService;
    private final SimpMessagingTemplate simpMessagingTemplate;
    private final UserSessionService sessionService;

    public PublicLoginService(JwtTokenUtil jwtUtil, RoomService roomService, SimpMessagingTemplate simpMessagingTemplate, UserSessionService sessionService) {
        this.jwtUtil = jwtUtil;
        this.roomService = roomService;
        this.simpMessagingTemplate = simpMessagingTemplate;
        this.sessionService = sessionService;
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

// This handles BOTH scenarios:
// 1. User in room → Exit room + delete session
// 2. User NOT in room → Just delete session

    public boolean logout(UserSession session) {
        if (session == null) {
            log.error("❌ Cannot logout: session is null");
            return false;
        }

        boolean success = true;
        String username = session.getUsername();
        String roomName = session.getRoomName();

        log.info("🚪 LOGOUT: User {} logging out", username);

        try {
            if (roomName != null) {
                // ==================== SCENARIO 1: USER IN ROOM ====================
                log.info("🏠 User {} is in room {}, exiting with full logout...", username, roomName);

                try {
                    // ✅ Exit room with fullLogout=true (deletes session)
                    boolean removed = roomService.exitFromRoom(roomName, username, true);

                    if (removed) {
                        log.info("✅ User {} exited room {} (session deleted)", username, roomName);

                        // Broadcast to remaining participants
                        try {
                            Room updatedRoom = roomService.getRoomDetails(roomName);

                            simpMessagingTemplate.convertAndSend(
                                    "/topic/chat/" + roomName + "/participants",
                                    updatedRoom.getParticipant()
                            );

                            log.info("✅ Broadcasted participant update for room {}", roomName);

                        } catch (RoomNotFoundException e) {
                            // Room deleted (last organizer) - this is expected behavior
                            log.info("ℹ️ Room {} deleted (last organizer left)", roomName);
                        } catch (Exception e) {
                            log.error("❌ Error broadcasting participant update: {}", e.getMessage());
                            success = false; // Non-critical error, but mark as partial failure
                        }
                    } else {
                        log.error("❌ Failed to remove user {} from room {}", username, roomName);
                        success = false;
                    }

                } catch (Exception e) {
                    log.error("❌ Error exiting room during logout: {}", e.getMessage(), e);
                    success = false;

                    // ⚠️ CRITICAL: Ensure session is deleted even if room exit fails
                    try {
                        sessionService.deleteUserSession(username);
                        log.info("✅ Session deleted as fallback after room exit error");
                    } catch (Exception ex) {
                        log.error("❌ CRITICAL: Failed to delete session as fallback: {}", ex.getMessage(), ex);
                        return false; // Total failure
                    }
                }

            } else {
                // ==================== SCENARIO 2: USER NOT IN ROOM ====================
                log.info("📋 User {} not in any room, deleting session only", username);

                try {
                    sessionService.deleteUserSession(username);
                    log.info("✅ Session deleted successfully");
                } catch (Exception e) {
                    log.error("❌ Error deleting session: {}", e.getMessage(), e);
                    return false; // Critical failure - session not deleted
                }
            }

            if (success) {
                log.info("✅ User {} logout complete (fully successful)", username);
            } else {
                log.warn("⚠️ User {} logout complete (with some errors)", username);
            }

            return success;

        } catch (Exception e) {
            log.error("❌ Unexpected error during logout for user {}: {}", username, e.getMessage(), e);

            // Last-ditch effort to clean up session
            try {
                sessionService.deleteUserSession(username);
                log.info("✅ Session deleted in final error recovery");
                return false; // Cleanup done but there were errors
            } catch (Exception ex) {
                log.error("❌ CRITICAL: Failed to delete session in final error recovery: {}", ex.getMessage(), ex);
                return false; // Total failure
            }
        }
    }
}
