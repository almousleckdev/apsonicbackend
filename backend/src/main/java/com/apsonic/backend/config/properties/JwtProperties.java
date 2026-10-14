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

    /** Signing keys that were committed to this repository's history. Compromised: never accepted. */
    private static final Set<String> COMPROMISED_SECRETS =
            Set.of("404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970");

    private String secret;
    private long expirationMs = 86400000L; // 24 hours
    private long refreshExpirationMs = 604800000L; // 7 days

    /** Fails application start-up when the HMAC key is missing, malformed, too short or known to be compromised. */
    @PostConstruct
    void validateSecret() {
        validateSecret(secret);
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
