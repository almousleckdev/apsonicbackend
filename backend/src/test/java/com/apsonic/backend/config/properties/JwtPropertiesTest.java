package com.apsonic.backend.config.properties;

import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtPropertiesTest {

    private static String randomBase64(int bytes) {
        byte[] b = new byte[bytes];
        new SecureRandom().nextBytes(b);
        return Base64.getEncoder().encodeToString(b);
    }

    @Test
    void acceptsStrongBase64Secret() {
        assertThatCode(() -> JwtProperties.validateSecret(randomBase64(48))).doesNotThrowAnyException();
    }

    @Test
    void rejectsMissingSecret() {
        assertThatThrownBy(() -> JwtProperties.validateSecret(null)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> JwtProperties.validateSecret("  ")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsTheCompromisedRepositoryDefault() {
        assertThatThrownBy(() -> JwtProperties.validateSecret(
                "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("publicly known");
    }

    @Test
    void rejectsShortAndNonBase64Secrets() {
        assertThatThrownBy(() -> JwtProperties.validateSecret(randomBase64(16)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("256 bits");
        assertThatThrownBy(() -> JwtProperties.validateSecret("not base64 !!!"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("base64");
    }
}
