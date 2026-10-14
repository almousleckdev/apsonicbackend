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

    /** Revokes an access token by its jti until the token would have expired anyway. */
    public void blacklistToken(String tokenId, long expirationTimeMs) {
        long ttlMs = expirationTimeMs - System.currentTimeMillis();
        if (ttlMs > 0) {
            redisTemplate.opsForValue().set(BLACKLIST_PREFIX + tokenId, "revoked", Duration.ofMillis(ttlMs));
        }
    }

    public boolean isTokenBlacklisted(String tokenId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(BLACKLIST_PREFIX + tokenId));
    }
}
