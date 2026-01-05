package com.music.musicwebapplication.listeners;

import com.music.musicwebapplication.dto.UserDisconnectedEvent;
import com.music.musicwebapplication.entity.UserSession;
import com.music.musicwebapplication.service.PublicLoginService;
import com.music.musicwebapplication.service.UserSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserDisconnectEventListener {

    private final UserSessionService sessionService;
    private final PublicLoginService loginService;

    // ✅ Track last cleanup time to prevent duplicates
    private final ConcurrentHashMap<String, Long> lastCleanupTime = new ConcurrentHashMap<>();
    private static final long CLEANUP_COOLDOWN_MS = 5000; // 5 seconds cooldown

    // ✅ CRITICAL FIX: Add delay before cleanup to allow flag update
    private static final long CLEANUP_DELAY_MS = 500; // 500ms delay

    @EventListener
    @Async // ✅ Run async to allow delay without blocking
    public void onUserDisconnected(UserDisconnectedEvent event) {
        String username = event.getUsername();
        String roomName = event.getRoomName();

        log.info("🧹 Handling disconnect cleanup for user={} room={}", username, roomName);

        // ✅ CRITICAL FIX: Check if cleanup was recently executed
        String cleanupKey = username + ":" + (roomName != null ? roomName : "no-room");
        long now = System.currentTimeMillis();

        Long lastTime = lastCleanupTime.get(cleanupKey);
        if (lastTime != null && (now - lastTime) < CLEANUP_COOLDOWN_MS) {
            log.warn("⏭️ Cleanup cooldown active for user={} room={} ({}ms ago) - skipping duplicate",
                    username, roomName, now - lastTime);
            return;
        }

        // ✅ CRITICAL FIX: Update cleanup timestamp BEFORE running cleanup
        lastCleanupTime.put(cleanupKey, now);

        try {
            // ✅ CRITICAL FIX: Delay cleanup to allow flag update to complete
            log.info("⏰ Delaying cleanup by {}ms to allow flag update", CLEANUP_DELAY_MS);
            Thread.sleep(CLEANUP_DELAY_MS);

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

        } catch (InterruptedException e) {
            log.error("❌ Cleanup interrupted for user {}", username);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("❌ Cleanup failed for disconnected user {}", username, e);
        } finally {
            // ✅ Clean up old entries to prevent memory leak
            lastCleanupTime.entrySet().removeIf(entry ->
                    (System.currentTimeMillis() - entry.getValue()) > 60000);
        }
    }
}