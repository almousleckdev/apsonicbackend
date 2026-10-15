package com.apsonic.backend.config.properties;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PreviewPropertiesTest {

    private PreviewProperties properties(String profile, String secret) {
        MockEnvironment env = new MockEnvironment();
        if (profile != null) {
            env.setActiveProfiles(profile);
        }
        PreviewProperties p = new PreviewProperties(env);
        p.setSecret(secret);
        return p;
    }

    @Test
    void productionRefusesToStartWithoutASecret() {
        assertThatThrownBy(() -> properties("prod", "").validate()).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PREVIEW_TOKEN_SECRET");
        assertThatThrownBy(() -> properties("prod", null).validate()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void nonProductionGeneratesATemporaryKey() {
        PreviewProperties p = properties("local", "");
        p.validate();
        assertThat(p.getSecret()).hasSizeGreaterThanOrEqualTo(32);
    }

    @Test
    void shortSecretsAreRejectedEverywhere() {
        assertThatThrownBy(() -> properties("local", "short").validate()).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> properties("prod", "short").validate()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void validSecretIsAccepted() {
        properties("prod", "x".repeat(40)).validate();
    }
}
