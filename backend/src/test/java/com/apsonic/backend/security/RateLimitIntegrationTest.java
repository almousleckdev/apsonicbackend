package com.apsonic.backend.security;

import com.apsonic.backend.BaseIntegrationTest;
import com.apsonic.backend.shared.ratelimit.RateLimiter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@org.springframework.test.context.TestPropertySource(properties = "app.rate-limit.enabled=true")
class RateLimitIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private RateLimiter rateLimiter;
    @Autowired
    private StringRedisTemplate redis;

    private static RequestPostProcessor from(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    /** A unique documentation-range style address per test so tests never share counters. */
    private static String freshIp() {
        return "198.51." + (int) (Math.random() * 250) + "." + (int) (Math.random() * 250);
    }

    private org.springframework.test.web.servlet.ResultActions login(String ip) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login").with(from(ip)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"usernameOrEmail\":\"nobody\",\"password\":\"Wrong-Pass#123\"}"));
    }

    @Test
    void loginIsLimitedPerClientAndOtherClientsAreUnaffected() throws Exception {
        String attacker = freshIp();
        for (int i = 0; i < 10; i++) {
            login(attacker).andExpect(status().isUnauthorized());
        }
        login(attacker)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.status").value(429));

        login(freshIp()).andExpect(status().isUnauthorized());
    }

    @Test
    void contactFormIsLimited() throws Exception {
        String ip = freshIp();
        for (int i = 0; i < 5; i++) {
            // Invalid body on purpose: the limiter counts the attempt before validation, and nothing is stored.
            mockMvc.perform(post("/api/v1/contact").with(from(ip)).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isBadRequest());
        }
        mockMvc.perform(post("/api/v1/contact").with(from(ip)).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void publicViewCounterIsLimited() throws Exception {
        String ip = freshIp();
        for (int i = 0; i < 20; i++) {
            mockMvc.perform(post("/api/v1/news/999999/views").with(from(ip))).andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(429));
        }
        mockMvc.perform(post("/api/v1/news/999999/views").with(from(ip))).andExpect(status().isTooManyRequests());
    }

    @Test
    void windowExpiresAndCounterAlwaysHasATtl() throws Exception {
        String bucket = "ttl-" + UUID.randomUUID();
        assertThat(rateLimiter.tryAcquire(bucket, "c", 2, Duration.ofSeconds(1)).allowed()).isTrue();
        assertThat(rateLimiter.tryAcquire(bucket, "c", 2, Duration.ofSeconds(1)).allowed()).isTrue();
        RateLimiter.Decision denied = rateLimiter.tryAcquire(bucket, "c", 2, Duration.ofSeconds(1));
        assertThat(denied.allowed()).isFalse();
        assertThat(denied.retryAfterSeconds()).isBetween(1L, 1L);
        // Never a counter without expiry (a permanent ban).
        assertThat(redis.getExpire("ratelimit:" + bucket + ":c")).isGreaterThan(-1L);

        Thread.sleep(1300);
        assertThat(rateLimiter.tryAcquire(bucket, "c", 2, Duration.ofSeconds(1)).allowed()).isTrue();
    }

    @Test
    void unlistedEndpointsAreNeverThrottled() throws Exception {
        String ip = freshIp();
        for (int i = 0; i < 40; i++) {
            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/categories").with(from(ip)))
                    .andExpect(status().isOk());
        }
    }
}
