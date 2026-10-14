package com.apsonic.backend.config.properties;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Base64;
import java.util.Set;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app.security.jwt")
public class JwtProperties {

    static final int MIN_KEY_BYTES = 32;
    static final long MAX_ACCESS_TOKEN_MS = 3_600_000L;

    /** Signing keys that were committed to this repository's history. Compromised: never accepted. */
    private static final Set<String> COMPROMISED_SECRETS =
            Set.of("404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970");

    private String secret;
    private long expirationMs = 900000L; // 15 minutes
    private String issuer = "apsonic-backend";
    private String audience = "apsonic-web";
    private long refreshExpirationMs = 604800000L; // 7 days

    /** Fails application start-up when the HMAC key is missing, malformed, too short or known to be compromised. */
    @PostConstruct
    void validate() {
        validateSecret(secret);
        validateLifetime(expirationMs);
    }

    static void validateLifetime(long accessTokenMs) {
        if (accessTokenMs <= 0 || accessTokenMs > MAX_ACCESS_TOKEN_MS) {
            throw new IllegalStateException("JWT_ACCESS_EXPIRATION_MS must be between 1 and " + MAX_ACCESS_TOKEN_MS
                    + " ms (1 hour). Access tokens must stay short-lived; sessions are renewed via the refresh cookie.");
        }
    }

    static void validateSecret(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            throw new IllegalStateException("JWT_SECRET is not set. Generate one with: openssl rand -base64 48");
        }
        if (COMPROMISED_SECRETS.contains(candidate)) {
            throw new IllegalStateException("JWT_SECRET is a publicly known default and must be replaced. "
                    + "Generate a new one with: openssl rand -base64 48");
        }
        byte[] key;
        try {
            key = Base64.getDecoder().decode(candidate);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("JWT_SECRET must be base64 encoded (openssl rand -base64 48)");
        }
        if (key.length < MIN_KEY_BYTES) {
            throw new IllegalStateException("JWT_SECRET must decode to at least " + MIN_KEY_BYTES + " bytes (256 bits)");
        }
    }
}
