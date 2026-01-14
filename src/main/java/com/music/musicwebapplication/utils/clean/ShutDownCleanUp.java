package com.music.musicwebapplication.utils.clean;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ShutDownCleanUp {

    private final RedisTemplate<String, Object> redisTemplate;

    public ShutDownCleanUp(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }


    @PreDestroy
    public void cleanup() {
        log.info("Application shutdown detected. Starting cleanup...");

        try {
            redisTemplate.execute((RedisCallback<Void>) connection -> {
                connection.flushAll();
                return null;
            });
            log.info("Redis cleanup completed successfully");
        } catch (Exception ex) {
            log.warn("Redis cleanup skipped (Redis may already be shutting down): {}", ex.getMessage());
        }
    }


}
