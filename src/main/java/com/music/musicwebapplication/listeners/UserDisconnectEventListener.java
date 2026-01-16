package com.music.musicwebapplication.listeners;

import com.music.musicwebapplication.dto.UserDisconnectedEvent;
import com.music.musicwebapplication.entity.UserSession;
import com.music.musicwebapplication.service.PublicAuthService;
import com.music.musicwebapplication.service.UserSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * ✅ SIMPLIFIED: No cooldown logic needed
 *
 * Since WebSocketDisconnectListener uses SessionDisconnectEvent which fires
 * EXACTLY ONCE per session, we no longer need cooldown timers or deduplication.
 *
 * This listener now simply handles the cleanup logic.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserDisconnectEventListener {

    private final UserSessionService sessionService;
    private final PublicAuthService loginService;

    @EventListener
    public void onUserDisconnected(UserDisconnectedEvent event) {
        String username = event.getUsername();
        String roomName = event.getRoomName();
        String sessionId = event.getSessionId();

        log.info("🧹 Handling disconnect cleanup for user={} room={} session={}",
                username, roomName, sessionId);

        try {
            UserSession session = sessionService.getUserSession(username);

            if (session == null) {
                log.warn("⚠️ No session found for disconnected user {}", username);
                return;
            }

            // ✅ Check the flag to decide action
            boolean shouldDeleteSession = session.isIntentionalLogout();

            log.info("🔍 Session flag check: intentionalLogout={}", shouldDeleteSession);

            if (roomName != null) {
                // Exit room based on flag
                boolean removed = loginService.logout(session);

                if (removed) {
                    log.info("✅ User {} removed from room {} (session {})",
                            username, roomName, shouldDeleteSession ? "DELETED" : "KEPT");
                }
            } else if (shouldDeleteSession) {
                // No room but full logout requested
                sessionService.deleteUserSession(username);
                log.info("✅ Session deleted for user {} (no room)", username);
            }

            // ✅ Reset flag after processing
            sessionService.resetIntentionalLogout(username);

            log.info("✅ Cleanup completed for disconnected user {}", username);

        } catch (Exception e) {
            log.error("❌ Cleanup failed for disconnected user {}", username, e);
        }
    }
}