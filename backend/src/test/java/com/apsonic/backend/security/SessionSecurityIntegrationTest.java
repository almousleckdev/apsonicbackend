package com.apsonic.backend.security;

import com.apsonic.backend.BaseIntegrationTest;
import com.apsonic.backend.config.properties.JwtProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Session lifecycle behaviour verified through the real filter chain, database and Redis. */
class SessionSecurityIntegrationTest extends BaseIntegrationTest {

    private static final String PASSWORD = "Sess!on-Test#Pass1";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JwtProperties jwtProperties;

    private record Session(String accessToken, String refreshToken, long userId) {
    }

    private Session login(String user, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("usernameOrEmail", user, "password", password))))
                .andExpect(status().isOk()).andReturn();
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        Cookie cookie = result.getResponse().getCookie("refresh_token");
        assertThat(cookie).isNotNull();
        return new Session(json.at("/data/accessToken").asText(), cookie.getValue(), json.at("/data/user/id").asLong());
    }

    private long createEditor(String adminToken, String username) throws Exception {
        MvcResult r = mockMvc.perform(post("/api/v1/admin/users").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", username, "email", username + "@apsonic.test",
                                "password", PASSWORD, "roles", List.of("ROLE_EDITOR")))))
                .andExpect(status().is2xxSuccessful()).andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString()).at("/data/id").asLong();
    }

    private org.springframework.test.web.servlet.ResultActions refresh(String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie("refresh_token", refreshToken))
                .header("X-Requested-With", "apsonic-web"));
    }

    private org.springframework.test.web.servlet.ResultActions me(String accessToken) throws Exception {
        return mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + accessToken));
    }

    @Test
    void accessTokenCarriesJtiIssuerAudienceAndIsShortLived() throws Exception {
        Session s = login(BOOTSTRAP_ADMIN_USERNAME, BOOTSTRAP_ADMIN_PASSWORD);
        String payload = new String(Base64.getUrlDecoder().decode(s.accessToken().split("\\.")[1]));
        JsonNode claims = objectMapper.readTree(payload);
        assertThat(claims.get("jti").asText()).isNotBlank();
        assertThat(claims.get("iss").asText()).isEqualTo(jwtProperties.getIssuer());
        assertThat(claims.get("aud").toString()).contains(jwtProperties.getAudience());
        assertThat(claims.get("exp").asLong() - claims.get("iat").asLong()).isLessThanOrEqualTo(3600);
    }

    @Test
    void tokenSignedWithRealKeyButWrongAudienceIsRejected() throws Exception {
        String forged = Jwts.builder().id(UUID.randomUUID().toString()).issuer(jwtProperties.getIssuer())
                .audience().add("some-other-service").and().subject(BOOTSTRAP_ADMIN_USERNAME).claim("id", 1)
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtProperties.getSecret()))).compact();
        me(forged).andExpect(status().isUnauthorized());
    }

    @Test
    void legacyTokenWithoutJtiIsRejected() throws Exception {
        String legacy = Jwts.builder().subject(BOOTSTRAP_ADMIN_USERNAME).claim("id", 1)
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtProperties.getSecret()))).compact();
        me(legacy).andExpect(status().isUnauthorized());
    }

    @Test
    void deactivatedUserLosesAccessImmediatelyAndCannotRefresh() throws Exception {
        Session admin = login(BOOTSTRAP_ADMIN_USERNAME, BOOTSTRAP_ADMIN_PASSWORD);
        String name = "editor-" + UUID.randomUUID().toString().substring(0, 8);
        long id = createEditor(admin.accessToken(), name);
        Session editor = login(name, PASSWORD);
        me(editor.accessToken()).andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/admin/users/" + id + "/status").param("enabled", "false")
                .header("Authorization", "Bearer " + admin.accessToken())).andExpect(status().isOk());

        me(editor.accessToken()).andExpect(status().isUnauthorized());
        refresh(editor.refreshToken()).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesTheAccessTokenAndRefreshCookie() throws Exception {
        Session s = login(BOOTSTRAP_ADMIN_USERNAME, BOOTSTRAP_ADMIN_PASSWORD);
        me(s.accessToken()).andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + s.accessToken())
                .cookie(new Cookie("refresh_token", s.refreshToken())).header("X-Requested-With", "apsonic-web"))
                .andExpect(status().isOk());

        me(s.accessToken()).andExpect(status().isUnauthorized());
        refresh(s.refreshToken()).andExpect(status().isUnauthorized());
    }

    @Test
    void refreshRotatesAndReplayOfTheOldTokenEndsEverySession() throws Exception {
        Session s = login(BOOTSTRAP_ADMIN_USERNAME, BOOTSTRAP_ADMIN_PASSWORD);

        MvcResult rotated = refresh(s.refreshToken()).andExpect(status().isOk()).andReturn();
        String newRefresh = rotated.getResponse().getCookie("refresh_token").getValue();
        assertThat(newRefresh).isNotEqualTo(s.refreshToken());

        // Outside the benign-race window a replay is treated as theft. Age the rotated row to simulate that.
        age(s.refreshToken());
        refresh(s.refreshToken()).andExpect(status().isUnauthorized());
        refresh(newRefresh).andExpect(status().isUnauthorized());
    }

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    private void age(String rawRefreshToken) {
        String hash = com.apsonic.backend.modules.auth.service.RefreshTokenCodec.hash(rawRefreshToken);
        jdbc.update("UPDATE refresh_tokens SET updated_at = now() - interval '1 hour' WHERE token = ?", hash);
    }
}
