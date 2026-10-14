package com.apsonic.backend.security;

import com.apsonic.backend.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Client mistakes and probing must produce 4xx, never a 500 (which also floods logs with stack traces). */
class ErrorHandlingIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void unknownPublicPathIsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/pages/debug-create2")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/news/does/not/exist/at/all")).andExpect(status().isNotFound());
    }

    @Test
    void wrongMethodIsMethodNotAllowed() throws Exception {
        mockMvc.perform(post("/api/v1/products/featured")).andExpect(status().isMethodNotAllowed());
    }

    @Test
    void malformedJsonIsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unsupportedMediaTypeIs415() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.TEXT_PLAIN).content("x"))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void invalidPathVariableTypeIsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users/not-a-number")).andExpect(status().isUnauthorized());
    }
}
