package com.apsonic.backend.modules.page.service;

import com.apsonic.backend.config.properties.PreviewProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Short-lived, signed links that let an editor (or a reviewer they share it with) see one unpublished page. A token
 * is bound to a single page id, carries a distinct audience so it is useless as a login token, and is checked only
 * by the preview endpoint.
 */
@Service
@RequiredArgsConstructor
public class PreviewTokenService {

    private static final String AUDIENCE = "apsonic-page-preview";
    private static final String SUBJECT = "page-preview";

    private final PreviewProperties properties;

    public record IssuedToken(String token, Instant expiresAt) {
    }

    public IssuedToken issue(long pageId) {
        Instant expiresAt = Instant.now().plusSeconds(properties.getTtlMinutes() * 60);
        String token = Jwts.builder()
                .subject(SUBJECT)
                .audience().add(AUDIENCE).and()
                .claim("pid", pageId)
                .id(UUID.randomUUID().toString())
                .issuedAt(new Date())
                .expiration(Date.from(expiresAt))
                .signWith(key(), Jwts.SIG.HS256)
                .compact();
        return new IssuedToken(token, expiresAt);
    }

    /** True only for an unexpired, correctly signed preview token issued for exactly this page. */
    public boolean isValidFor(String token, long pageId) {
        if (token == null || token.isBlank() || token.length() > 1024) {
            return false;
        }
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key())
                    .requireSubject(SUBJECT)
                    .requireAudience(AUDIENCE)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            Number id = claims.get("pid", Number.class);
            return id != null && id.longValue() == pageId;
        } catch (Exception invalidOrExpired) {
            return false;
        }
    }

    private SecretKey key() {
        return Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
    }
}
