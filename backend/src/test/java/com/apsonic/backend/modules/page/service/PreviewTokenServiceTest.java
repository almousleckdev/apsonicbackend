package com.apsonic.backend.modules.page.service;

import com.apsonic.backend.config.properties.PreviewProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class PreviewTokenServiceTest {

    private static final String SECRET = "unit-test-preview-secret-0123456789-abcdef";

    private PreviewTokenService service;

    @BeforeEach
    void setUp() {
        PreviewProperties properties = new PreviewProperties(new MockEnvironment());
        properties.setSecret(SECRET);
        service = new PreviewTokenService(properties);
    }

    private String token(String audience, String subject, long pageId, long expiresInMs, String secret) {
        return Jwts.builder().subject(subject).audience().add(audience).and().claim("pid", pageId)
                .expiration(new Date(System.currentTimeMillis() + expiresInMs))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256).compact();
    }

    @Test
    void issuedTokenIsValidForItsPageOnly() {
        String token = service.issue(7).token();
        assertThat(service.isValidFor(token, 7)).isTrue();
        assertThat(service.isValidFor(token, 8)).isFalse();
    }

    @Test
    void expiredTokenIsRejected() {
        String expired = token("apsonic-page-preview", "page-preview", 7, -1_000, SECRET);
        assertThat(service.isValidFor(expired, 7)).isFalse();
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() {
        String forged = token("apsonic-page-preview", "page-preview", 7, 60_000, "another-secret-another-secret-another-secret");
        assertThat(service.isValidFor(forged, 7)).isFalse();
    }

    @Test
    void tokenForAnotherPurposeIsRejectedEvenWithTheRightKey() {
        assertThat(service.isValidFor(token("apsonic-web", "page-preview", 7, 60_000, SECRET), 7)).isFalse();
        assertThat(service.isValidFor(token("apsonic-page-preview", "someone", 7, 60_000, SECRET), 7)).isFalse();
    }

    @Test
    void tamperedAndGarbageTokensAreRejected() {
        String token = service.issue(7).token();
        String tampered = token.substring(0, token.length() - 4) + (token.endsWith("AAAA") ? "BBBB" : "AAAA");
        assertThat(service.isValidFor(tampered, 7)).isFalse();
        assertThat(service.isValidFor("not-a-token", 7)).isFalse();
        assertThat(service.isValidFor("", 7)).isFalse();
        assertThat(service.isValidFor(null, 7)).isFalse();
        assertThat(service.isValidFor("a".repeat(5000), 7)).isFalse();
    }

    @Test
    void unsignedTokenIsRejected() {
        String unsigned = Jwts.builder().subject("page-preview").audience().add("apsonic-page-preview").and().claim("pid", 7)
                .expiration(new Date(System.currentTimeMillis() + 60_000)).compact();
        assertThat(service.isValidFor(unsigned, 7)).isFalse();
    }

    @Test
    void tokensDoNotContainThePageAddress() {
        assertThat(service.issue(7).expiresAt()).isAfter(java.time.Instant.now());
    }
}
