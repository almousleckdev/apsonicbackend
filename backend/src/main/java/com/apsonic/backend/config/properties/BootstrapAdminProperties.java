package com.apsonic.backend.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Optional credentials for creating the very first Super Admin. Supplied only through the
 * environment (BOOTSTRAP_ADMIN_USERNAME / _EMAIL / _PASSWORD); there are deliberately no defaults.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.bootstrap-admin")
public class BootstrapAdminProperties {

    private String username;
    private String email;
    private String password;

    public boolean isConfigured() {
        return hasText(username) && hasText(email) && hasText(password);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
