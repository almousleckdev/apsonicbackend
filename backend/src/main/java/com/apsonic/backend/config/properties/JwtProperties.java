package com.apsonic.backend.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app.security.jwt")
public class JwtProperties {

    private String secret;
    private long expirationMs = 86400000L; // 24 hours
    private long refreshExpirationMs = 604800000L; // 7 days
}
