package com.apsonic.backend.modules.auth;

import com.apsonic.backend.BaseIntegrationTest;
import com.apsonic.backend.shared.mail.EmailService;
import com.apsonic.backend.shared.mail.OutgoingEmail;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(PasswordResetIntegrationTest.RecordingMail.class)
class PasswordResetIntegrationTest extends BaseIntegrationTest {

    private static final String STRONG = "New-Strong#Pass1";
    private static final Pattern TOKEN = Pattern.compile("token=([A-Za-z0-9_-]+)");

    @TestConfiguration
    static class RecordingMail {
        static final List<OutgoingEmail> SENT = new CopyOnWriteArrayList<>();

        @Bean
        @Primary
        EmailService recordingEmailService() {
            return SENT::add;
        }
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    @Qualifier("mailTaskExecutor")
    private ThreadPoolTaskExecutor mailExecutor;

    /** Mail is sent in the background; wait for the previous test's messages to land before starting a new one. */
    private void awaitMailQueueDrained() {
        await().atMost(Duration.ofSeconds(5)).until(() ->
                mailExecutor.getThreadPoolExecutor().getActiveCount() == 0
                        && mailExecutor.getThreadPoolExecutor().getQueue().isEmpty());
    }

    @BeforeEach
    void clearMailbox() {
        awaitMailQueueDrained();
        RecordingMail.SENT.clear();
    }

    private String adminToken() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "usernameOrEmail", BOOTSTRAP_ADMIN_USERNAME, "password", BOOTSTRAP_ADMIN_PASSWORD));
        String response = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).at("/data/accessToken").asText();
    }

    /** Creates a user with an initial password through the admin API and returns the unique email. */
    private String createUserWithPassword(String password) throws Exception {
        String id = UUID.randomUUID().toString().substring(0, 8);
        String email = "user-" + id + "@apsonic.test";
        String body = objectMapper.writeValueAsString(Map.of(
                "username", "user" + id, "email", email, "password", password, "roles", List.of("ADMIN")));
        mockMvc.perform(post("/api/v1/admin/users").header("Authorization", "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated());
        RecordingMail.SENT.clear();
        return email;
    }

    private JsonNode createUserWithoutPassword(String email) throws Exception {
        String id = UUID.randomUUID().toString().substring(0, 8);
        String body = objectMapper.writeValueAsString(Map.of(
                "username", "invitee" + id, "email", email, "firstName", "张", "lastName", "伟",
                "roles", List.of("EDITOR")));
        MvcResult result = mockMvc.perform(post("/api/v1/admin/users").header("Authorization", "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
    }

    private void forgot(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", email)))).andExpect(status().isOk());
    }

    private String lastTokenSentTo(String email) {
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(RecordingMail.SENT).anyMatch(m -> m.to().equals(email)));
        OutgoingEmail mail = RecordingMail.SENT.stream().filter(m -> m.to().equals(email)).reduce((a, b) -> b).orElseThrow();
        Matcher matcher = TOKEN.matcher(mail.text());
        assertThat(matcher.find()).as("a reset link in the email").isTrue();
        return matcher.group(1);
    }

    private int resetStatus(String token, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("token", token, "password", password))))
                .andReturn().getResponse().getStatus();
    }

    private int loginStatus(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("usernameOrEmail", email, "password", password))))
                .andReturn().getResponse().getStatus();
    }

    @Test
    void forgotPasswordAnswersTheSameForKnownAndUnknownEmails() throws Exception {
        String known = createUserWithPassword("Old-Strong#Pass1");

        String a = mockMvc.perform(post("/api/v1/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", known))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String b = mockMvc.perform(post("/api/v1/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "nobody@apsonic.test"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readTree(a).get("message")).isEqualTo(objectMapper.readTree(b).get("message"));
    }

    @Test
    void unknownEmailGetsNoMail() throws Exception {
        String unknown = "nobody-" + UUID.randomUUID() + "@apsonic.test";
        forgot(unknown);
        awaitMailQueueDrained();
        assertThat(RecordingMail.SENT).noneMatch(m -> m.to().equals(unknown));
    }

    @Test
    void resetEmailIsInChineseAndNewPasswordWorksOnlyOnce() throws Exception {
        String email = createUserWithPassword("Old-Strong#Pass1");
        forgot(email);
        String token = lastTokenSentTo(email);

        OutgoingEmail mail = RecordingMail.SENT.getFirst();
        assertThat(mail.subject()).containsPattern("[\\u4e00-\\u9fff]");
        assertThat(mail.text()).containsPattern("[\\u4e00-\\u9fff]").contains("/admin/reset-password?token=");
        assertThat((mail.subject() + mail.text()).replaceAll("https?://\\S+", "")).doesNotContainPattern("[A-Za-z]{4,}\\s[A-Za-z]{3,}");

        assertThat(resetStatus(token, STRONG)).isEqualTo(200);
        assertThat(loginStatus(email, STRONG)).isEqualTo(200);
        assertThat(loginStatus(email, "Old-Strong#Pass1")).isEqualTo(401);
        assertThat(resetStatus(token, "Another-Strong#Pass2")).isEqualTo(400);
        assertThat(loginStatus(email, STRONG)).isEqualTo(200);
    }

    @Test
    void weakPasswordIsRejectedAndTheLinkStaysUsable() throws Exception {
        String email = createUserWithPassword("Old-Strong#Pass1");
        forgot(email);
        String token = lastTokenSentTo(email);

        assertThat(resetStatus(token, "weak")).isEqualTo(400);
        assertThat(resetStatus(token, STRONG)).isEqualTo(200);
    }

    @Test
    void garbageAndExpiredTokensAreRejected() throws Exception {
        assertThat(resetStatus("not-a-real-token", STRONG)).isEqualTo(400);

        String email = createUserWithPassword("Old-Strong#Pass1");
        forgot(email);
        String token = lastTokenSentTo(email);
        jdbc.update("UPDATE password_tokens SET expires_at = NOW() - INTERVAL '1 minute'");
        assertThat(resetStatus(token, STRONG)).isEqualTo(400);
    }

    @Test
    void aNewRequestInvalidatesTheOlderLink() throws Exception {
        String email = createUserWithPassword("Old-Strong#Pass1");
        forgot(email);
        String first = lastTokenSentTo(email);
        RecordingMail.SENT.clear();
        forgot(email);
        String second = lastTokenSentTo(email);

        assertThat(second).isNotEqualTo(first);
        assertThat(resetStatus(first, STRONG)).isEqualTo(400);
        assertThat(resetStatus(second, STRONG)).isEqualTo(200);
    }

    @Test
    void tokensAreStoredHashedNeverInTheClear() throws Exception {
        String email = createUserWithPassword("Old-Strong#Pass1");
        forgot(email);
        String token = lastTokenSentTo(email);

        Integer clear = jdbc.queryForObject("SELECT COUNT(*) FROM password_tokens WHERE token_hash = ?", Integer.class, token);
        assertThat(clear).isZero();
    }

    @Test
    void resettingThePasswordSignsOutExistingSessions() throws Exception {
        String email = createUserWithPassword("Old-Strong#Pass1");
        assertThat(loginStatus(email, "Old-Strong#Pass1")).isEqualTo(200);
        forgot(email);

        assertThat(resetStatus(lastTokenSentTo(email), STRONG)).isEqualTo(200);

        Integer sessions = jdbc.queryForObject(
                "SELECT COUNT(*) FROM refresh_tokens rt JOIN users u ON u.id = rt.user_id WHERE u.email = ?", Integer.class, email);
        assertThat(sessions).isZero();
    }

    @Test
    void disabledAccountsGetNoResetMail() throws Exception {
        String email = createUserWithPassword("Old-Strong#Pass1");
        jdbc.update("UPDATE users SET enabled = false WHERE email = ?", email);

        forgot(email);
        awaitMailQueueDrained();

        assertThat(RecordingMail.SENT).noneMatch(m -> m.to().equals(email));
    }

    @Test
    void creatingAUserWithoutPasswordSendsAnInvitationThatSetsThePassword() throws Exception {
        String email = "invitee-" + UUID.randomUUID() + "@apsonic.test";

        JsonNode created = createUserWithoutPassword(email);

        assertThat(created.get("invitationSent").asBoolean()).isTrue();
        OutgoingEmail mail = RecordingMail.SENT.getFirst();
        assertThat(mail.to()).isEqualTo(email);
        assertThat(mail.text()).containsPattern("[\\u4e00-\\u9fff]").contains("/admin/reset-password?token=").contains("24");
        String token = lastTokenSentTo(email);

        assertThat(loginStatus(email, STRONG)).isEqualTo(401);
        assertThat(resetStatus(token, STRONG)).isEqualTo(200);
        assertThat(loginStatus(email, STRONG)).isEqualTo(200);
    }

    @Test
    void invitationLinkLastsTwentyFourHours() throws Exception {
        String email = "invitee-" + UUID.randomUUID() + "@apsonic.test";
        createUserWithoutPassword(email);

        Long hours = jdbc.queryForObject(
                "SELECT ROUND(EXTRACT(EPOCH FROM (t.expires_at - t.created_at)) / 3600) FROM password_tokens t "
                        + "JOIN users u ON u.id = t.user_id WHERE u.email = ?", Long.class, email);
        assertThat(hours).isEqualTo(24L);
    }

    @Test
    void anInvitationCanBeResentAndTheOldLinkStopsWorking() throws Exception {
        String email = "invitee-" + UUID.randomUUID() + "@apsonic.test";
        long id = createUserWithoutPassword(email).get("id").asLong();
        String first = lastTokenSentTo(email);
        RecordingMail.SENT.clear();

        mockMvc.perform(post("/api/v1/admin/users/" + id + "/invitation").header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk());

        String second = lastTokenSentTo(email);
        assertThat(resetStatus(first, STRONG)).isEqualTo(400);
        assertThat(resetStatus(second, STRONG)).isEqualTo(200);
    }

    private JsonNode listedUser(String email) throws Exception {
        String response = mockMvc.perform(get("/api/v1/admin/users").param("query", email)
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        for (JsonNode user : objectMapper.readTree(response).at("/data/content")) {
            if (email.equals(user.get("email").asText())) {
                return user;
            }
        }
        throw new AssertionError("user not listed: " + email);
    }

    @Test
    void anInvitedPersonIsNotActiveUntilTheyChooseAPassword() throws Exception {
        String email = "invitee-" + UUID.randomUUID() + "@apsonic.test";

        JsonNode created = createUserWithoutPassword(email);
        assertThat(created.get("status").asText()).isEqualTo("INVITED");
        assertThat(created.get("invitationExpiresAt").isNull()).isFalse();
        assertThat(listedUser(email).get("status").asText()).isEqualTo("INVITED");

        assertThat(resetStatus(lastTokenSentTo(email), STRONG)).isEqualTo(200);

        JsonNode active = listedUser(email);
        assertThat(active.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(active.get("invitationExpiresAt").isNull()).isTrue();
    }

    @Test
    void anExpiredInvitationIsShownAsExpiredAndCanBeSentAgain() throws Exception {
        String email = "invitee-" + UUID.randomUUID() + "@apsonic.test";
        long id = createUserWithoutPassword(email).get("id").asLong();
        jdbc.update("UPDATE password_tokens SET expires_at = NOW() - INTERVAL '1 minute' "
                + "WHERE user_id = (SELECT id FROM users WHERE email = ?)", email);

        assertThat(listedUser(email).get("status").asText()).isEqualTo("INVITATION_EXPIRED");

        RecordingMail.SENT.clear();
        mockMvc.perform(post("/api/v1/admin/users/" + id + "/invitation").header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk());
        assertThat(listedUser(email).get("status").asText()).isEqualTo("INVITED");
        assertThat(resetStatus(lastTokenSentTo(email), STRONG)).isEqualTo(200);
    }

    @Test
    void aSuspendedInvitationShowsAsSuspendedAndCannotBeResent() throws Exception {
        String email = "invitee-" + UUID.randomUUID() + "@apsonic.test";
        long id = createUserWithoutPassword(email).get("id").asLong();

        mockMvc.perform(patch("/api/v1/admin/users/" + id + "/status").param("enabled", "false")
                .header("Authorization", "Bearer " + adminToken())).andExpect(status().isOk());

        assertThat(listedUser(email).get("status").asText()).isEqualTo("SUSPENDED");
        mockMvc.perform(post("/api/v1/admin/users/" + id + "/invitation").header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anInvitationCannotBeResentToSomeoneWhoAlreadyHasAPassword() throws Exception {
        String email = "invitee-" + UUID.randomUUID() + "@apsonic.test";
        long id = createUserWithoutPassword(email).get("id").asLong();
        assertThat(resetStatus(lastTokenSentTo(email), STRONG)).isEqualTo(200);

        mockMvc.perform(post("/api/v1/admin/users/" + id + "/invitation").header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void forgotPasswordLinksAlsoLastTwentyFourHoursAndWorkOnce() throws Exception {
        String email = createUserWithPassword("Old-Strong#Pass1");
        forgot(email);
        String token = lastTokenSentTo(email);

        Long hours = jdbc.queryForObject(
                "SELECT ROUND(EXTRACT(EPOCH FROM (t.expires_at - t.created_at)) / 3600) FROM password_tokens t "
                        + "JOIN users u ON u.id = t.user_id WHERE u.email = ? AND t.purpose = 'RESET'", Long.class, email);
        assertThat(hours).isEqualTo(24L);
        assertThat(resetStatus(token, STRONG)).isEqualTo(200);
        assertThat(resetStatus(token, "Another-Strong#Pass2")).isEqualTo(400);
    }

    @Test
    void anonymousCannotResendInvitations() throws Exception {
        mockMvc.perform(post("/api/v1/admin/users/1/invitation")).andExpect(status().is4xxClientError());
    }
}
