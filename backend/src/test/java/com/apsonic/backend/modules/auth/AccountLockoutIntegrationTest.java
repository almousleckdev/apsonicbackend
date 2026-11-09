package com.apsonic.backend.modules.auth;

import com.apsonic.backend.BaseIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Five wrong passwords block the account for 30 minutes. Only an administrator can lift the block earlier: neither the
 * right password nor a password-reset link does.
 */
class AccountLockoutIntegrationTest extends BaseIntegrationTest {

    private static final String PASSWORD = "Strong#Pass-1234";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper mapper;
    @Autowired
    private JdbcTemplate jdbc;

    private String token(String user, String password) throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("usernameOrEmail", user, "password", password))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(response).at("/data/accessToken").asText();
    }

    private String adminToken() throws Exception {
        return token(BOOTSTRAP_ADMIN_USERNAME, BOOTSTRAP_ADMIN_PASSWORD);
    }

    private record Account(long id, String username) {
    }

    private Account createAccount(String role) throws Exception {
        String name = "lock-" + UUID.randomUUID().toString().substring(0, 8);
        String response = mockMvc.perform(post("/api/v1/admin/users").header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("username", name, "email", name + "@apsonic.test",
                                "password", PASSWORD, "roles", List.of(role)))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return new Account(mapper.readTree(response).at("/data/id").asLong(), name);
    }

    private int login(String user, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("usernameOrEmail", user, "password", password))))
                .andReturn().getResponse().getStatus();
    }

    private void failTimes(Account a, int times) throws Exception {
        for (int i = 0; i < times; i++) {
            assertThat(login(a.username(), "wrong-" + i)).isEqualTo(401);
        }
    }

    private Account lockedAccount() throws Exception {
        Account a = createAccount("ROLE_EDITOR");
        failTimes(a, 4);
        assertThat(login(a.username(), "wrong-5th")).isEqualTo(423);
        return a;
    }

    private JsonNode listed(Account a) throws Exception {
        String response = mockMvc.perform(get("/api/v1/admin/users").param("query", a.username())
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        for (JsonNode user : mapper.readTree(response).at("/data/content")) {
            if (a.username().equals(user.get("username").asText())) {
                return user;
            }
        }
        throw new AssertionError("not listed");
    }

    @Test
    void fiveWrongPasswordsBlockTheAccountAndTheRightPasswordDoesNotHelp() throws Exception {
        Account a = lockedAccount();

        assertThat(login(a.username(), PASSWORD)).isEqualTo(423);
        assertThat(listed(a).get("status").asText()).isEqualTo("LOCKED");
    }

    @Test
    void theBlockLastsThirtyMinutesThenEnds() throws Exception {
        Account a = lockedAccount();

        jdbc.update("UPDATE users SET lock_time = NOW() - INTERVAL '29 minutes' WHERE id = ?", a.id());
        assertThat(login(a.username(), PASSWORD)).isEqualTo(423);

        jdbc.update("UPDATE users SET lock_time = NOW() - INTERVAL '31 minutes' WHERE id = ?", a.id());
        assertThat(login(a.username(), PASSWORD)).isEqualTo(200);
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT account_non_locked, failed_login_attempts, lock_time FROM users WHERE id = ?", a.id());
        assertThat(row.get("account_non_locked")).isEqualTo(true);
        assertThat(((Number) row.get("failed_login_attempts")).intValue()).isZero();
        assertThat(row.get("lock_time")).isNull();
    }

    @Test
    void anExpiredBlockStartsAFreshCountOfFiveNotOne() throws Exception {
        Account a = lockedAccount();
        jdbc.update("UPDATE users SET lock_time = NOW() - INTERVAL '31 minutes' WHERE id = ?", a.id());

        failTimes(a, 4);
        assertThat(login(a.username(), PASSWORD)).isEqualTo(200);
    }

    @Test
    void aSuccessfulSignInInTheMiddleResetsTheCount() throws Exception {
        Account a = createAccount("ROLE_EDITOR");
        failTimes(a, 3);
        assertThat(login(a.username(), PASSWORD)).isEqualTo(200);
        failTimes(a, 3);

        assertThat(login(a.username(), PASSWORD)).isEqualTo(200);
    }

    @Test
    void anAdministratorCanUnlockAndTheEditorCannot() throws Exception {
        Account a = lockedAccount();
        Account editor = createAccount("ROLE_EDITOR");
        String editorToken = token(editor.username(), PASSWORD);

        mockMvc.perform(post("/api/v1/admin/users/" + a.id() + "/unlock").header("Authorization", "Bearer " + editorToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/admin/users/" + a.id() + "/unlock")).andExpect(status().is4xxClientError());
        assertThat(login(a.username(), PASSWORD)).isEqualTo(423);

        mockMvc.perform(post("/api/v1/admin/users/" + a.id() + "/unlock").header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk());

        assertThat(login(a.username(), PASSWORD)).isEqualTo(200);
        assertThat(listed(a).get("status").asText()).isEqualTo("ACTIVE");
    }

    @Test
    void aPasswordResetLinkDoesNotLiftTheBlock() throws Exception {
        Account a = lockedAccount();
        Long userId = a.id();
        // A reset token for the locked account, as the "forgot password" email would deliver.
        String raw = "reset-" + UUID.randomUUID();
        jdbc.update("INSERT INTO password_tokens (user_id, token_hash, purpose, expires_at) VALUES (?, ?, 'RESET', NOW() + INTERVAL '1 hour')",
                userId, sha256(raw));

        mockMvc.perform(post("/api/v1/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("token", raw, "password", "New-Strong#Pass-9"))))
                .andExpect(status().isOk());

        assertThat(login(a.username(), "New-Strong#Pass-9")).isEqualTo(423);
        Boolean unlocked = jdbc.queryForObject("SELECT account_non_locked FROM users WHERE id = ?", Boolean.class, userId);
        assertThat(unlocked).isFalse();
    }

    @Test
    void parallelGuessesCannotSlipPastTheLimit() throws Exception {
        Account a = createAccount("ROLE_EDITOR");
        ExecutorService pool = Executors.newFixedThreadPool(12);
        try {
            List<Callable<Integer>> guesses = new ArrayList<>();
            for (int i = 0; i < 12; i++) {
                int n = i;
                guesses.add(() -> login(a.username(), "guess-" + n));
            }
            for (Future<Integer> f : pool.invokeAll(guesses)) {
                assertThat(f.get()).isIn(401, 423);
            }
        } finally {
            pool.shutdownNow();
        }

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT account_non_locked, failed_login_attempts FROM users WHERE id = ?", a.id());
        assertThat(row.get("account_non_locked")).isEqualTo(false);
        assertThat(((Number) row.get("failed_login_attempts")).intValue()).isGreaterThanOrEqualTo(5);
        assertThat(login(a.username(), PASSWORD)).isEqualTo(423);
    }

    @Test
    void unknownAccountsAreAnsweredLikeAWrongPassword() throws Exception {
        for (int i = 0; i < 7; i++) {
            assertThat(login("nobody-" + UUID.randomUUID(), "x")).isEqualTo(401);
        }
    }

    private static String sha256(String raw) throws Exception {
        byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return java.util.HexFormat.of().formatHex(digest);
    }
}
