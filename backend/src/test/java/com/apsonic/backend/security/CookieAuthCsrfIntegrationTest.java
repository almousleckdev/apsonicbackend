package com.apsonic.backend.security;

import com.apsonic.backend.BaseIntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CookieAuthCsrfIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private static final Cookie COOKIE = new Cookie("refresh_token", "whatever");

    @Test
    void refreshWithoutCustomHeaderIsBlocked() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(COOKIE).header("Origin", ALLOWED_ORIGIN))
                .andExpect(status().isForbidden());
    }

    @Test
    void logoutWithoutCustomHeaderIsBlocked() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout").cookie(COOKIE)).andExpect(status().isForbidden());
    }

    @Test
    void refreshFromForeignOriginIsBlockedEvenWithHeader() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(COOKIE)
                        .header("Origin", "https://evil.example").header("X-Requested-With", "apsonic-web"))
                .andExpect(status().isForbidden());
    }

    @Test
    void refreshFromAllowedOriginWithHeaderReachesTheEndpoint() throws Exception {
        // Reaches token validation (401 for an unknown token) instead of being stopped by the guard (403).
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(COOKIE)
                        .header("Origin", ALLOWED_ORIGIN).header("X-Requested-With", "apsonic-web"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void corsPreflightFromForeignOriginIsDenied() throws Exception {
        mockMvc.perform(options("/api/v1/auth/refresh").header("Origin", "https://evil.example")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "x-requested-with"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void corsDoesNotAllowArbitraryRequestHeaders() throws Exception {
        mockMvc.perform(options("/api/v1/auth/refresh").header("Origin", ALLOWED_ORIGIN)
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "x-evil-header"))
                .andExpect(status().isForbidden());
    }
}
