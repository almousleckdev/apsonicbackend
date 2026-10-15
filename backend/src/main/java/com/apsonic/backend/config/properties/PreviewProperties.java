package com.apsonic.backend.config.properties;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Signing key for page preview links. It is separate from the JWT secret so a leaked preview link can never be
 * confused with a login token. Required in production; outside production a random per-process key is used (preview
 * links then stop working when the application restarts, which is acceptable for development).
 */
@Slf4j
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app.preview")
public class PreviewProperties {

    static final int MIN_LENGTH = 32;

    private final Environment environment;
    private String secret;
    private long ttlMinutes = 15;

    public PreviewProperties(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    void validate() {
        if (ttlMinutes < 1 || ttlMinutes > 60) {
            throw new IllegalStateException("app.preview.ttl-minutes must be between 1 and 60");
        }
        if (secret == null || secret.isBlank()) {
            if (environment.acceptsProfiles(Profiles.of("prod"))) {
                throw new IllegalStateException("PREVIEW_TOKEN_SECRET is not set. Generate one with: openssl rand -base64 48");
            }
            byte[] random = new byte[48];
            new SecureRandom().nextBytes(random);
            secret = Base64.getEncoder().encodeToString(random);
            log.info("PREVIEW_TOKEN_SECRET is not set; using a temporary key for this run (preview links expire on restart)");
            return;
        }
        if (secret.length() < MIN_LENGTH) {
            throw new IllegalStateException("PREVIEW_TOKEN_SECRET must be at least " + MIN_LENGTH + " characters");
        }
    }
}
