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
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.SocketException;
import java.net.SocketTimeoutException;
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

    // Read from application.properties with default fallback
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
        log.debug("UserSessionService initialized with session TTL: {} hours", sessionTtlHours);
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
            log.debug("Skipping Redis operation - application not active");
            return true;
        }
        return false;
    }

    // ---------------------------------------------------------
    // SESSION MANAGEMENT
    // ---------------------------------------------------------

    @Transactional
    public boolean saveSession(String token, String username) {
        log.debug("Attempting to save session for user: {}", username);
        LocalDateTime now = LocalDateTime.now();
//        No needed because already verified.
//        if (repo.existsByUsername(username)) {
//            return false;
//        }

        UserSession session = new UserSession();
        session.setToken(token);
        session.setUsername(username);
        session.setRoomName(null);
        session.setAbsoluteExpiry(now.plusHours(1)); // for testing
        session.setSessionExpired(false);
        try{
            saveUserSessionInDbWithRetry(session);
            log.debug("Session saved to database for user: {}", username);

            // Set Redis TTL for session expiration
            setRedisSessionTTL(username);
            log.info("Session saved successfully for user {} with {} hour TTL", username, sessionTtlHours);
            return true;
        }catch (Exception e){
            log.error("Error while saving session for user {}: {}", username, e.getMessage(), e);
            return false;
        }

    }

    @Retryable(
            retryFor = {SocketException.class, SocketTimeoutException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public void saveUserSessionInDbWithRetry(UserSession u){
        try{
            repo.save(u);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    @Transactional
    public void updateRoomName(String username, String roomName) {
        log.debug("Updating room name for user: {} to: {}", username, roomName);
        Optional<UserSession> sessionOpt = repo.findByUsername(username);

        if (sessionOpt.isPresent()) {
            UserSession session = sessionOpt.get();
            session.setRoomName(roomName);
            saveUserSessionInDbWithRetry(session);

            log.info("Room name updated successfully for user {} to: {}", username, roomName);
        } else {
            log.warn("Cannot update room name - no session found for user: {}", username);
        }
    }


    /**
     * Resets the intentional logout flag
     */
    @Transactional
    public void resetIntentionalLogout(String username) {
        log.debug("Resetting intentional logout flag for user: {}", username);
        Optional<UserSession> sessionOpt = repo.findByUsername(username);
        if (sessionOpt.isPresent()) {
            UserSession session = sessionOpt.get();
            session.setIntentionalLogout(true);
            saveUserSessionInDbWithRetry(session);
            log.debug("Intentional logout flag reset successfully for user: {}", username);
        } else {
            log.warn("Cannot reset logout flag - no session found for user: {}", username);
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
            log.debug("Redis TTL set to {} hours for session: {}", sessionTtlHours, username);
        } catch (IllegalStateException e) {
            log.warn("Redis connection unavailable for user {}: {}", username, e.getMessage());
        } catch (Exception e) {
            log.error("Failed to set Redis TTL for user {}: {}", username, e.getMessage(), e);
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
                log.debug("Redis TTL refreshed for session: {}", username);
            } else {
                // Key expired or doesn't exist - recreate it
                log.debug("Redis key not found for user {}, recreating", username);
                setRedisSessionTTL(username);
            }
        } catch (IllegalStateException e) {
            log.warn("Redis connection unavailable for user {}: {}", username, e.getMessage());
        } catch (Exception e) {
            log.error("Failed to refresh Redis TTL for user {}: {}", username, e.getMessage(), e);
        }
    }

    /**
     * Checks if session is still valid in Redis
     */
    public boolean isSessionActiveInRedis(String username) {
        if (canAccessRedis()) return false;

        try {
            String redisKey = "session:" + username;
            boolean active = redisTemplate.hasKey(redisKey);
            log.debug("Redis session check for user {}: {}", username, active ? "active" : "inactive");
            return active;
        } catch (IllegalStateException e) {
            log.warn("Redis connection unavailable for user {}: {}", username, e.getMessage());
            return false;
        } catch (Exception e) {
            log.error("Failed to check Redis session for user {}: {}", username, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Removes session from Redis
     */
    private void removeRedisSession(String username) {
        if (canAccessRedis()) {
            log.debug("Skipping Redis removal for user {} - application shutting down", username);
            return;
        }

        try {
            String redisKey = "session:" + username;
            Boolean deleted = redisTemplate.delete(redisKey);
            if (deleted) {
                log.debug("Redis session removed for user: {}", username);
            } else {
                log.debug("Redis session key not found for user: {}", username);
            }
        } catch (IllegalStateException e) {
            log.warn("Redis connection unavailable during cleanup for user {}: {}", username, e.getMessage());
        } catch (Exception e) {
            log.error("Failed to remove Redis session for user {}: {}", username, e.getMessage(), e);
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
            log.warn("Skipping scheduled cleanup - application not active");
            return;
        }

        log.info("Starting scheduled session cleanup");

        try {
            LocalDateTime cutoffTime = LocalDateTime.now().minusMinutes(sessionTtlHours);

            // Find all sessions
            List<UserSession> allSessions = repo.findAll();
            log.debug("Found {} total sessions to check", allSessions.size());

            int expiredCount = 0;
            int staleCount = 0;

            for (UserSession session : allSessions) {
                String username = session.getUsername();

                // Check 1: Session expired in Redis
                if (!isSessionActiveInRedis(username)) {
                    log.debug("Removing expired session for user: {} (Redis TTL expired)", username);
                    repo.delete(session);
                    expiredCount++;
                    continue;
                }

                // Check 2: Session with null roomName older than TTL
                if (session.getRoomName() == null &&
                        session.getLastAccessedAt() != null &&
                        session.getLastAccessedAt().isBefore(cutoffTime)) {

                    log.debug("Removing stale session for user: {} (no room, last accessed: {})",
                            username, session.getLastAccessedAt());
                    removeRedisSession(username);
                    repo.delete(session);
                    staleCount++;
                }
            }

            log.info("Session cleanup completed: {} expired sessions removed, {} stale sessions removed",
                    expiredCount, staleCount);

        } catch (Exception e) {
            log.error("Error during scheduled session cleanup: {}", e.getMessage(), e);
        }
    }


    // ==================== EXISTING METHODS ====================

    public UserSession getUserSessionForToken(String token) {
        log.debug("Fetching user session by token");
        return repo.findUserSessionByToken(token);
    }


    public Optional<String> getToken(String username) {
        log.debug("Fetching token for user: {}", username);
        return repo.findByUsername(username).map(UserSession::getToken);
    }

    @Transactional
    public void deleteUserSession(String username) {
//        log.info("Deleting session for user: {}", username);

        // Remove from Redis
        removeRedisSession(username);

        // Remove from database
        repo.deleteByUsername(username);

        log.info("Session deleted successfully for user: {}", username);
    }


    @PreDestroy
    private void clearUserSessions() {
        log.info("UserSessionService shutting down - starting cleanup");
        shuttingDown = true;

        // Clear all Redis session keys
        try {
            List<UserSession> allSessions = repo.findAll();
            log.info("Clearing {} Redis session keys", allSessions.size());

            for (UserSession session : allSessions) {
                try {
                    String redisKey = "session:" + session.getUsername();
                    redisTemplate.delete(redisKey);
                } catch (Exception e) {
                    // Ignore errors during shutdown
                    log.debug("Could not delete Redis session during shutdown for user {}: {}",
                            session.getUsername(), e.getMessage());
                }
            }
            log.info("Redis sessions cleared successfully");
        } catch (Exception e) {
            log.error("Error clearing Redis sessions during shutdown: {}", e.getMessage());
        }

        // Clear database
        try {
            repo.deleteAll();
            log.info("Database sessions cleared successfully");
        } catch (Exception e) {
            log.error("Error clearing database sessions during shutdown: {}", e.getMessage());
        }

        log.info("UserSessionService shutdown completed");
    }

    public UserSession getUserSession(String username) {
        log.debug("Fetching session for user: {}", username);
        Optional<UserSession> user = repo.findByUsername(username);

        // Refresh activity on access
        if (user.isPresent()) {
            refreshRedisSessionTTL(username);
        }

        return user.orElse(null);
    }



    public void updateSession(UserSession session) {
        log.debug("Updating session for user: {}", session.getUsername());
        saveUserSessionInDbWithRetry(session);
    }


    public List<UserSession> getExpiredSessions() {
        log.debug("Fetching expired sessions");
        LocalDateTime now = LocalDateTime.now();
        List<UserSession> expiredSessions = repo.findByAbsoluteExpiryBeforeAndSessionExpiredFalse(now);
        log.info("Found {} expired sessions", expiredSessions.size());
        return expiredSessions;
    }

    public String updateRequestFlagStatus(String token, boolean newFlag) {
        log.debug("Updating request flag status for token to: {}", newFlag);
        UserSession session = repo.findUserSessionByToken(token);

        if (session == null) {
            log.warn("No session found for provided token");
            return "NOT_FOUND";
        }

        log.debug("Current intentional logout flag: {}, new flag: {}", session.isIntentionalLogout(), newFlag);

        if (session.isIntentionalLogout() == newFlag) {
            log.debug("Flag value unchanged - no update required");
            return "EXIST";
        }

        session.setIntentionalLogout(newFlag);
        saveUserSessionInDbWithRetry(session);

        log.info("Intentional logout flag updated to {} for user: {}", newFlag, session.getUsername());
        return "SUCCESS";
    }
}