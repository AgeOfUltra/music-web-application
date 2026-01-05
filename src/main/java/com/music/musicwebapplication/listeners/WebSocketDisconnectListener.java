package com.music.musicwebapplication.listeners;

import com.music.musicwebapplication.dto.UserDisconnectedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.concurrent.TimeUnit;

/**
 * ✅ ROOT CAUSE FIX: Use SessionDisconnectEvent instead of DISCONNECT command
 *
 * Spring fires SessionDisconnectEvent EXACTLY ONCE per WebSocket session,
 * not once per subscription. This eliminates duplicate events entirely.
 *
 * Benefits:
 * - ✅ Spring guarantees this fires only once per session
 * - ✅ No need for cooldown timers or deduplication logic
 * - ✅ Cleaner, more reliable code
 * - ✅ Proper separation of concerns
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketDisconnectListener implements ApplicationListener<SessionDisconnectEvent> {

    private final ApplicationEventPublisher eventPublisher;

    // ✅ Add delay to allow flag update to complete before cleanup
    private static final long CLEANUP_DELAY_MS = 500;

    @Override
    @Async // ✅ Run async to allow delay without blocking
    public void onApplicationEvent(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());

        String username = (String) accessor.getSessionAttributes().get("username");
        String roomName = (String) accessor.getSessionAttributes().get("roomId");
        String sessionId = accessor.getSessionId();

        log.info("📴 WebSocket SESSION DISCONNECT → user={}, room={}, sessionId={}",
                username, roomName, sessionId);

        if (username != null) {
            try {
                // ✅ Delay to allow flag update request to complete
                log.info("⏰ Delaying cleanup by {}ms to allow flag update", CLEANUP_DELAY_MS);
                TimeUnit.MILLISECONDS.sleep(CLEANUP_DELAY_MS);

                log.info("🚀 Publishing UserDisconnectedEvent for user={} room={}",
                        username, roomName);

                eventPublisher.publishEvent(
                        new UserDisconnectedEvent(username, roomName, sessionId)
                );

            } catch (InterruptedException e) {
                log.error("❌ Cleanup interrupted for user {}", username);
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.error("❌ Error publishing disconnect event for user {}", username, e);
            }
        } else {
            log.warn("⚠️ No username found in session attributes for disconnected session {}", sessionId);
        }
    }
}
