package com.music.musicwebapplication.listeners;

import com.music.musicwebapplication.dto.UserDisconnectedEvent;
import com.music.musicwebapplication.entity.UserSession;
import com.music.musicwebapplication.service.PublicLoginService;
import com.music.musicwebapplication.service.UserSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserDisconnectEventListener {

    private final UserSessionService sessionService;
    private final PublicLoginService loginService;

    @EventListener
    public void onUserDisconnected(UserDisconnectedEvent event) {

        String username = event.getUsername();
        String roomName = event.getRoomName();

        log.info("🧹 Handling disconnect cleanup for user={} room={}", username, roomName);

        try {
            UserSession session = sessionService.getUserSession(username);

            if (session == null) {
                log.warn("⚠️ No session found for disconnected user {}", username);
                return;
            }

            // 🔥 REUSE YOUR EXISTING LOGOUT LOGIC
            loginService.logout(session);

            log.info("✅ Cleanup completed for disconnected user {}", username);

        } catch (Exception e) {
            log.error("❌ Cleanup failed for disconnected user {}", username, e);
        }
    }
}

