package com.music.musicwebapplication.service;

import com.music.musicwebapplication.entity.UserSession;
import com.music.musicwebapplication.repo.UserSessionRepo;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class UserSessionService {
    private final UserSessionRepo repo;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ApplicationContext applicationContext;

    // ✅ Read from application.properties with default fallback
    @Value("${session.ttl.hours:24}")
    private int sessionTtlHours;

    // Shutdown flag
    private volatile boolean shuttingDown = false;

    @Autowired
    public UserSessionService(UserSessionRepo repo,
                              RedisTemplate<String, Object> redisTemplate,
                              ApplicationContext applicationContext) {
        this.repo = repo;
        this.redisTemplate = redisTemplate;
        this.applicationContext = applicationContext;
    }

    // ---------------------------------------------------------
    // HELPER METHODS
    // ---------------------------------------------------------

    private boolean isApplicationActive() {
        if (shuttingDown) {
            return false;
        }
        try {
            return applicationContext instanceof ConfigurableApplicationContext
                    && ((ConfigurableApplicationContext) applicationContext).isActive();
        } catch (Exception e) {
            return false;
        }
    }

    private boolean canAccessRedis() {
        if (!isApplicationActive()) {
            log.debug("⚠️ Skipping Redis operation - application not active");
            return true;
        }
        return false;
    }

    // ---------------------------------------------------------
    // SESSION MANAGEMENT
    // ---------------------------------------------------------

    public boolean saveSession(String token, String username) {
        LocalDateTime now = LocalDateTime.now();
        if (repo.existsByUsername(username)) {
            return false;
        }

        UserSession session = new UserSession();
        session.setToken(token);
        session.setUsername(username);
        session.setRoomName(null);
        session.setAbsoluteExpiry(now.plusHours(1)); // for testing
        session.setSessionExpired(false);
        repo.save(session);

        // ✅ Set Redis TTL for session expiration
        setRedisSessionTTL(username);

        log.info("💾 Session saved for {} with {}h TTL", username, sessionTtlHours);

        return true;
    }

    @Transactional
    public void updateRoomName(String username, String roomName) {
        Optional<UserSession> sessionOpt = repo.findByUsername(username);

        if (sessionOpt.isPresent()) {
            UserSession session = sessionOpt.get();
            session.setRoomName(roomName);
            repo.save(session);

            log.info("✅ Updated roomName for {} to: {}", username, roomName);
        } else {
            log.warn("⚠️ No session found for user: {}", username);
        }
    }


    /**
     * Resets the intentional logout flag
     */
    @Transactional
    public void resetIntentionalLogout(String username) {
        Optional<UserSession> sessionOpt = repo.findByUsername(username);
        if (sessionOpt.isPresent()) {
            UserSession session = sessionOpt.get();
            session.setIntentionalLogout(true);
            repo.save(session);
            log.debug("🔄 Reset intentionalLogout flag for {}", username);
        }
    }

    // ==================== REDIS TTL MANAGEMENT ====================

    /**
     * Sets initial TTL for a session in Redis
     */
    private void setRedisSessionTTL(String username) {
        if (canAccessRedis()) return;

        try {
            String redisKey = "session:" + username;
            // Store a marker in Redis with TTL
            redisTemplate.opsForValue().set(redisKey, "ACTIVE", sessionTtlHours, TimeUnit.HOURS);
            log.debug("⏱️ Set Redis TTL {}h for session: {}", sessionTtlHours, username);
        } catch (IllegalStateException e) {
            log.warn("⚠️ Redis connection unavailable for {}: {}", username, e.getMessage());
        } catch (Exception e) {
            log.error("❌ Failed to set Redis TTL for {}: {}", username, e.getMessage());
        }
    }

    /**
     * Refreshes TTL when user is active
     */
    private void refreshRedisSessionTTL(String username) {
        if (canAccessRedis()) return;

        try {
            String redisKey = "session:" + username;
            // Refresh TTL on user activity
            Boolean exists = redisTemplate.hasKey(redisKey);
            if (exists) {
                redisTemplate.expire(redisKey, sessionTtlHours, TimeUnit.HOURS);
                log.info("🔄 Refreshed Redis TTL for session: {}", username);
            } else {
                // Key expired or doesn't exist - recreate it
                setRedisSessionTTL(username);
            }
        } catch (IllegalStateException e) {
            log.warn("⚠️ Redis connection unavailable for {}: {}", username, e.getMessage());
        } catch (Exception e) {
            log.error("❌ Failed to refresh Redis TTL for {}: {}", username, e.getMessage());
        }
    }

    /**
     * Checks if session is still valid in Redis
     */
    public boolean isSessionActiveInRedis(String username) {
        if (canAccessRedis()) return false;

        try {
            String redisKey = "session:" + username;
            return redisTemplate.hasKey(redisKey);
        } catch (IllegalStateException e) {
            log.warn("⚠️ Redis connection unavailable for {}: {}", username, e.getMessage());
            return false;
        } catch (Exception e) {
            log.error("❌ Failed to check Redis session for {}: {}", username, e.getMessage());
            return false;
        }
    }

    /**
     * Removes session from Redis
     */
    private void removeRedisSession(String username) {
        if (canAccessRedis()) {
            log.debug("⚠️ Skipping Redis removal for {} - application shutting down", username);
            return;
        }

        try {
            String redisKey = "session:" + username;
            Boolean deleted = redisTemplate.delete(redisKey);
            if (deleted) {
                log.debug("🗑️ Removed Redis session for: {}", username);
            }
        } catch (IllegalStateException e) {
            log.warn("⚠️ Redis connection unavailable during cleanup: {}", e.getMessage());
        } catch (Exception e) {
            log.error("❌ Failed to remove Redis session for {}: {}", username, e.getMessage());
        }
    }

    // ==================== SCHEDULED CLEANUP JOB ====================

    /**
     * Scheduled job to clean up stale sessions
     * Runs every 6 hours
     */
    @Scheduled(cron = "0 0 */1 * * *")
    @Transactional
    public void cleanupStaleSessions() {
        if (canAccessRedis()) {
            log.warn("⚠️ Skipping scheduled cleanup - application not active");
            return;
        }

        log.info("🧹 Starting scheduled session cleanup...");

        try {
            LocalDateTime cutoffTime = LocalDateTime.now().minusMinutes(sessionTtlHours);

            // Find all sessions
            List<UserSession> allSessions = repo.findAll();
            int expiredCount = 0;
            int staleCount = 0;
            for (UserSession session : allSessions) {
                String username = session.getUsername();

                // Check 1: Session expired in Redis
                if (!isSessionActiveInRedis(username)) {
                    log.info("🗑️ Removing expired session: {} (Redis TTL expired)", username);
                    repo.delete(session);
                    expiredCount++;
                    continue;
                }

                // Check 2: Session with null roomName older than TTL
                if (session.getRoomName() == null &&
                        session.getLastAccessedAt() != null &&
                        session.getLastAccessedAt().isBefore(cutoffTime)) {

                    log.info("🗑️ Removing stale session: {} (no room, last accessed: {})",
                            username, session.getLastAccessedAt());
                    removeRedisSession(username);
                    repo.delete(session);
                    staleCount++;
                }
            }

            log.info("✅ Session cleanup complete: {} expired, {} stale removed",
                    expiredCount, staleCount);

        } catch (Exception e) {
            log.error("❌ Error during scheduled session cleanup: {}", e.getMessage(), e);
        }
    }


    // ==================== EXISTING METHODS ====================

    public UserSession getUserSessionForToken(String token) {
        return repo.findUserSessionByToken(token);
    }


    public Optional<String> getToken(String username) {
        return repo.findByUsername(username).map(UserSession::getToken);
    }

    @Transactional
    public void deleteUserSession(String username) {
        // Remove from Redis
        removeRedisSession(username);

        // Remove from database
        repo.deleteByUsername(username);

        log.info("🗑️ Deleted session for user {}", username);
    }


    @PreDestroy
    private void clearUserSessions() {
        log.info("🛑 UserSessionService shutting down...");
        shuttingDown = true;

        log.info("🧹 All sessions deletion started...");

        // Clear all Redis session keys
        try {
            List<UserSession> allSessions = repo.findAll();
            for (UserSession session : allSessions) {
                try {
                    String redisKey = "session:" + session.getUsername();
                    redisTemplate.delete(redisKey);
                } catch (Exception e) {
                    // Ignore errors during shutdown
                    log.debug("⚠️ Could not delete Redis session during shutdown: {}", e.getMessage());
                }
            }
            log.info("✅ Redis sessions cleared");
        } catch (Exception e) {
            log.error("❌ Error clearing Redis sessions: {}", e.getMessage());
        }

        // Clear database
        try {
            repo.deleteAll();
            log.info("✅ Database sessions cleared");
        } catch (Exception e) {
            log.error("❌ Error clearing database sessions: {}", e.getMessage());
        }

        log.info("✅ All sessions deletion completed");
    }

    public UserSession getUserSession(String username) {
        Optional<UserSession> user = repo.findByUsername(username);

        // ✅ Refresh activity on access
        if (user.isPresent()) {
            refreshRedisSessionTTL(username);
        }

        return user.orElse(null);
    }



    public void updateSession(UserSession session) {
        repo.save(session);
    }


    public List<UserSession> getExpiredSessions() {
        LocalDateTime now = LocalDateTime.now();
        return repo.findByAbsoluteExpiryBeforeAndSessionExpiredFalse(now);
    }

    public String updateRequestFlagStatus(String token, boolean newFlag) {
        UserSession session = repo.findUserSessionByToken(token);
        if (session == null) {
            log.warn("⚠️ No session found for token");
            return "NOT_FOUND";
        }

        log.info("Current flag : {} and new flag {}", session.isIntentionalLogout(), newFlag);
        if (session.isIntentionalLogout() == newFlag) {
            log.info("Current flag is same as new flag! no update required!");
            return "EXIST";
        }
        session.setIntentionalLogout(newFlag);
        repo.save(session);

        log.info("Intentional flag is saved to {}", session.isIntentionalLogout());
        return "SUCCESS";
    }
}