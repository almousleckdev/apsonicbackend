package com.apsonic.backend.shared.ratelimit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * Fixed-window counter in Redis, so limits hold across all application instances. The INCR and the first
 * EXPIRE run in one Lua script: a crash between them can never leave a counter without a TTL (a permanent ban).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimiter {

    private static final String PREFIX = "ratelimit:";

    private static final DefaultRedisScript<Long> INCREMENT_WITH_TTL = new DefaultRedisScript<>("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end
            return count
            """, Long.class);

    private final StringRedisTemplate redis;

    public record Decision(boolean allowed, long retryAfterSeconds) {
        static Decision allow() {
            return new Decision(true, 0);
        }
    }

    public Decision tryAcquire(String bucket, String clientKey, int limit, Duration window) {
        String key = PREFIX + bucket + ":" + clientKey;
        try {
            Long count = redis.execute(INCREMENT_WITH_TTL, List.of(key), String.valueOf(window.toSeconds()));
            if (count != null && count > limit) {
                Long ttl = redis.getExpire(key);
                return new Decision(false, ttl == null || ttl < 1 ? window.toSeconds() : ttl);
            }
            return Decision.allow();
        } catch (RuntimeException redisUnavailable) {
            // Fail open: an outage of the limiter must not take login or the contact form down with it.
            // Per-account lockout still protects credentials.
            log.error("Rate limiter unavailable for bucket '{}': {}", bucket, redisUnavailable.getClass().getSimpleName());
            return Decision.allow();
        }
    }
}
