package com.apsonic.backend.security.jwt;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

    private final StringRedisTemplate redisTemplate;
    private static final String BLACKLIST_PREFIX = "jwt:blacklist:";

    public void blacklistToken(String token, long expirationTimeMs) {
        long ttlMs = expirationTimeMs - System.currentTimeMillis();
        if (ttlMs > 0) {
            redisTemplate.opsForValue().set(BLACKLIST_PREFIX + token, "revoked", Duration.ofMillis(ttlMs));
        }
    }

    public boolean isTokenBlacklisted(String token) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(BLACKLIST_PREFIX + token));
    }
}
