package com.apsonic.backend.security;

import com.apsonic.backend.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

class ApiSecurityHeadersIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void apiResponsesCarryHardeningHeaders() throws Exception {
        mockMvc.perform(get("/api/v1/categories"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'"))
                .andExpect(header().string("Permissions-Policy", containsString("camera=()")));
    }

    @Test
    void errorResponsesAreHardenedToo() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'"));
    }

    @Test
    void uploadsKeepTheirOwnSandboxPolicyNotTheApiOne() throws Exception {
        mockMvc.perform(get("/uploads/nonexistent.png"))
                .andExpect(header().string("Content-Security-Policy", containsString("sandbox")));
    }
}
