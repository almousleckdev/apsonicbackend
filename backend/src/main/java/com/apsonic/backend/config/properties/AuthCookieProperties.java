package com.apsonic.backend.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app.security.cookie")
public class AuthCookieProperties {

    private String name = "refresh_token";
    private String path = "/api/v1/auth";
    private boolean secure = true;
    private String sameSite = "Strict";
}
