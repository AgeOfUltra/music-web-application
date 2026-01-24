package com.music.musicwebapplication.schedulers;

import com.music.musicwebapplication.entity.User;
import com.music.musicwebapplication.entity.UserSession;
import com.music.musicwebapplication.service.PublicAuthService;
import com.music.musicwebapplication.service.UserSessionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@Slf4j
public class UnUsedSessionCleanUp {

    private final UserSessionService sessionService;
    private final PublicAuthService loginService;

    public UnUsedSessionCleanUp(UserSessionService sessionService, PublicAuthService loginService) {
        this.sessionService = sessionService;
        this.loginService = loginService;
    }

    @Scheduled(cron = "0 */30 * * * *")
    public void cleanUpExpiredSession() {
        log.info("🧹 Starting scheduled cleanup of expired sessions");

        try {
            // Find sessions where absoluteExpiry has passed
            List<UserSession> expiredSessions = sessionService.getExpiredSessions();

            if (expiredSessions.isEmpty()) {
                log.debug("ℹ️ No expired sessions to clean");
                return;
            }

            log.debug("🗑️ Found {} expired sessions to clean", expiredSessions.size());

            for (UserSession session : expiredSessions) {
                try {
                    // Mark as expired BEFORE cleanup (prevents race conditions)
                    session.setSessionExpired(true);
                    sessionService.updateSession(session);

                    boolean success = loginService.logout(session);

                    if (success) {
                        log.info("✅ Cleaned expired session for user: {}", session.getUsername());
                    } else {
                        log.warn("⚠️ Partial cleanup for user: {}", session.getUsername());
                    }

                } catch (Exception e) {
                    log.error("❌ Error cleaning session for user {}: {}",
                            session.getUsername(), e.getMessage());
                    // Continue with next session (don't let one failure stop cleanup)
                }
            }

            log.info("✅ Scheduled cleanup completed");

        } catch (Exception e) {
            log.error("❌ Scheduler error: {}", e.getMessage(), e);
        }
    }

    @Scheduled(cron = "0 */10 * * * *")
    public void deleteUnVerifiedAccounts(){
        Optional<List<User>> users = loginService.getOlderThan5MinUsers();
        if(users.isEmpty()){
            log.debug("No records to Update");
        }
        users.get().forEach(
                loginService::deleteWithRetry
        );
        log.info("CleanUpDone for Expired Verifications");
    }



}
