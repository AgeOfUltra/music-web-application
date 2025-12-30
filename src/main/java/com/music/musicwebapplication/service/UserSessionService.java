package com.music.musicwebapplication.service;

import com.music.musicwebapplication.entity.UserSession;
import com.music.musicwebapplication.repo.UserSessionRepo;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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

    // ✅ Read from application.properties with default fallback
    @Value("${session.ttl.hours:24}")
    private int sessionTtlHours;

    @Autowired
    public UserSessionService(UserSessionRepo repo, RedisTemplate<String, Object> redisTemplate) {
        this.repo = repo;
        this.redisTemplate = redisTemplate;
    }

    public boolean saveSession(String token, String username) {
        if (repo.existsByUsername(username)) {
            return false;
        }

        UserSession session = new UserSession();
        session.setToken(token);
        session.setUsername(username);
        session.setRoomName(null);
        session.setIntentionalLogout(false);
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


    @Transactional
    public void setIntentionalLogout(String username, boolean intentional) {
        Optional<UserSession> sessionOpt = repo.findByUsername(username);
        if (sessionOpt.isPresent()) {
            UserSession session = sessionOpt.get();
            session.setIntentionalLogout(intentional);
            repo.save(session);
            log.info("🔖 Set intentionalLogout={} for user {}", intentional, username);
        } else {
            log.warn("⚠️ Could not set intentionalLogout flag - no session for user {}", username);
        }
    }

    /**
     * Checks if the user's exit should delete the session
     */
    public boolean shouldDeleteSession(String username) {
        Optional<UserSession> sessionOpt = repo.findByUsername(username);
        if (sessionOpt.isEmpty()) {
            return false;
        }

        UserSession session = sessionOpt.get();
        boolean intentional = session.isIntentionalLogout();

        log.info("🔍 Checking logout intent for {}: intentionalLogout={}", username, intentional);

        return intentional;
    }

    /**
     * Resets the intentional logout flag
     */
    @Transactional
    public void resetIntentionalLogout(String username) {
        Optional<UserSession> sessionOpt = repo.findByUsername(username);
        if (sessionOpt.isPresent()) {
            UserSession session = sessionOpt.get();
            session.setIntentionalLogout(false);
            repo.save(session);
            log.debug("🔄 Reset intentionalLogout flag for {}", username);
        }
    }

    /**
     * Updates last accessed time for session activity tracking
     */
//    @Transactional
//    public void updateLastAccessedTime(String username) {
//        Optional<UserSession> sessionOpt = repo.findByUsername(username);
//        if (sessionOpt.isPresent()) {
//            UserSession session = sessionOpt.get();
//            session.setLastAccessedAt(LocalDateTime.now());
//            repo.save(session);
//        }
//    }

    // ==================== REDIS TTL MANAGEMENT ====================

    /**
     * Sets initial TTL for a session in Redis
     */
    private void setRedisSessionTTL(String username) {
        try {
            String redisKey = "session:" + username;
            // Store a marker in Redis with TTL
            redisTemplate.opsForValue().set(redisKey, "ACTIVE", sessionTtlHours, TimeUnit.HOURS);
            log.debug("⏱️ Set Redis TTL {}h for session: {}", sessionTtlHours, username);
        } catch (Exception e) {
            log.error("❌ Failed to set Redis TTL for {}: {}", username, e.getMessage());
        }
    }

    /**
     * Refreshes TTL when user is active
     */
    private void refreshRedisSessionTTL(String username) {
        try {
            String redisKey = "session:" + username;
            // Refresh TTL on user activity
            Boolean exists = redisTemplate.hasKey(redisKey);
            if (exists) {
                redisTemplate.expire(redisKey, sessionTtlHours, TimeUnit.HOURS);
                log.debug("🔄 Refreshed Redis TTL for session: {}", username);
            } else {
                // Key expired or doesn't exist - recreate it
                setRedisSessionTTL(username);
            }
        } catch (Exception e) {
            log.error("❌ Failed to refresh Redis TTL for {}: {}", username, e.getMessage());
        }
    }

    /**
     * Checks if session is still valid in Redis
     */
    public boolean isSessionActiveInRedis(String username) {
        try {
            String redisKey = "session:" + username;
            return redisTemplate.hasKey(redisKey);
        } catch (Exception e) {
            log.error("❌ Failed to check Redis session for {}: {}", username, e.getMessage());
            return false;
        }
    }

    /**
     * Removes session from Redis
     */
    private void removeRedisSession(String username) {
        try {
            String redisKey = "session:" + username;
            redisTemplate.delete(redisKey);
            log.debug("🗑️ Removed Redis session for: {}", username);
        } catch (Exception e) {
            log.error("❌ Failed to remove Redis session for {}: {}", username, e.getMessage());
        }
    }

    // ==================== SCHEDULED CLEANUP JOB ====================

    /**
     * Scheduled job to clean up stale sessions
     * Runs every 6 hours
     */
    @Scheduled(cron = "0 0 */6 * * *")
    @Transactional
    public void cleanupStaleSessions() {
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

    /**
     * Manual cleanup trigger (for testing or admin use)
     */
    @Transactional
    public void cleanupExpiredSessionsNow() {
        log.info("🧹 Manual session cleanup triggered...");
        cleanupStaleSessions();
    }

    // ==================== EXISTING METHODS ====================

    public Optional<List<UserSession>> getUserSessionByEmptyRoom() {
        return repo.getUserSessionByRoomNameEmpty();
    }

    public UserSession getUserSessionForToken(String token) {
        return repo.findUserSessionByToken(token);
    }

    public Optional<String> getRoomName(String username) {
        return repo.findByUsername(username).map(UserSession::getRoomName);
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
        log.info("All sessions deletion started...");

        // Clear all Redis session keys
        try {
            List<UserSession> allSessions = repo.findAll();
            for (UserSession session : allSessions) {
                removeRedisSession(session.getUsername());
            }
        } catch (Exception e) {
            log.error("Error clearing Redis sessions: {}", e.getMessage());
        }

        // Clear database
        repo.deleteAll();
        log.info("All sessions deletion completed...");
    }

    public UserSession getUserSession(String username) {
        Optional<UserSession> user = repo.findByUsername(username);

        // ✅ Refresh activity on access
        if (user.isPresent()) {
//            updateLastAccessedTime(username);
            refreshRedisSessionTTL(username);
        }

        return user.orElse(null);
    }

    public void updateUsesSessionExpiry(String token){
        UserSession currentSession = getUserSessionForToken(token);
        currentSession.setSessionExpired(true);
        repo.save(currentSession);
    }
    public List<UserSession> getInactiveUsers(){
        return repo.getUserSessionBySessionExpired();
    }
}