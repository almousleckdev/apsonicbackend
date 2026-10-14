package com.apsonic.backend.modules.auth.controller;

import com.apsonic.backend.config.properties.CorsProperties;
import com.apsonic.backend.config.properties.JwtProperties;
import com.apsonic.backend.modules.auth.dto.request.LoginRequest;
import com.apsonic.backend.modules.auth.dto.response.AuthResponse;
import com.apsonic.backend.modules.auth.dto.response.TokenRefreshResponse;
import com.apsonic.backend.modules.auth.dto.response.UserProfileResponse;
import com.apsonic.backend.modules.auth.service.AuthService;
import com.apsonic.backend.modules.auth.service.AuthenticationResult;
import com.apsonic.backend.security.jwt.JwtAuthenticationFilter;
import com.apsonic.backend.security.jwt.JwtTokenProvider;
import com.apsonic.backend.shared.cookie.RefreshCookieFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@WebMvcTest(
        controllers = AuthController.class,
        excludeAutoConfiguration = {
        DataSourceAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class,
        FlywayAutoConfiguration.class,
        RedisAutoConfiguration.class
})
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @MockitoBean
    private AuthService authService;
    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;
    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockitoBean
    private JwtProperties jwtProperties;
    @MockitoBean
    private CorsProperties corsProperties;
    @MockitoBean
    private RefreshCookieFactory refreshCookieFactory;
    private static final String COOKIE_NAME = "refresh_token";

    @Test
    void login_Success_SetsHttpOnlyCookieAndOmitsTokenFromBody() throws Exception {
        LoginRequest loginRequest = new LoginRequest("superadmin", "Admin@123");

        AuthResponse authResponse = AuthResponse.builder()
                .accessToken("mock_access_token")
                .tokenType("Bearer")
                .expiresIn(86400L)
                .user(UserProfileResponse.builder().id(1L).username("superadmin").build())
                .build();

        given(authService.login(any())).willReturn(new AuthenticationResult<>(authResponse, "mock_refresh_token"));
        given(refreshCookieFactory.create("mock_refresh_token")).willReturn(realCookie("mock_refresh_token"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("mock_access_token"))
                .andExpect(jsonPath("$.data.user.username").value("superadmin"))
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist())
                .andExpect(cookie().exists(COOKIE_NAME))
                .andExpect(cookie().httpOnly(COOKIE_NAME, true))
                .andExpect(cookie().value(COOKIE_NAME, "mock_refresh_token"));
    }

    @Test
    void refreshToken_Success_RotatesCookie() throws Exception {
        TokenRefreshResponse refreshResponse = TokenRefreshResponse.builder()
                .accessToken("new_access_token")
                .tokenType("Bearer")
                .expiresIn(86400L)
                .build();

        given(authService.refreshToken("incoming_refresh_token"))
                .willReturn(new AuthenticationResult<>(refreshResponse, "rotated_refresh_token"));
        given(refreshCookieFactory.create("rotated_refresh_token")).willReturn(realCookie("rotated_refresh_token"));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie(COOKIE_NAME, "incoming_refresh_token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("new_access_token"))
                .andExpect(cookie().value(COOKIE_NAME, "rotated_refresh_token"));
    }

    @Test
    void refreshToken_MissingCookie_Returns401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logout_ClearsCookie() throws Exception {
        given(refreshCookieFactory.clear()).willReturn(clearedCookie());

        mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(new Cookie(COOKIE_NAME, "some_token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(cookie().maxAge(COOKIE_NAME, 0));
    }

    // Helpers methods
    private static ResponseCookie realCookie(String value) {
        return ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/api/v1/auth")
                .maxAge(Duration.ofDays(7))
                .build();
    }

    private static ResponseCookie clearedCookie() {
        return ResponseCookie.from(COOKIE_NAME, "")
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/api/v1/auth")
                .maxAge(Duration.ZERO)
                .build();
    }
}
