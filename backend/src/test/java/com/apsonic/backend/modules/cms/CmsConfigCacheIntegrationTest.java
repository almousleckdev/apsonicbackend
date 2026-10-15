package com.apsonic.backend.modules.cms;

import com.apsonic.backend.BaseIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The public CMS config endpoint (it serves the homepage content edited in the admin) is cached in Redis.
 * It once returned 500 for every request because the cached value could not be serialised, which silently made the
 * website ignore everything saved in the admin. These tests run against a real Redis.
 */
class CmsConfigCacheIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private StringRedisTemplate redis;

    private String adminToken() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "usernameOrEmail", BOOTSTRAP_ADMIN_USERNAME, "password", BOOTSTRAP_ADMIN_PASSWORD));
        String response = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).at("/data/accessToken").asText();
    }

    private void save(String token, String key, String value) throws Exception {
        mockMvc.perform(put("/api/v1/admin/cms/config/" + key).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("configValue", value))))
                .andExpect(status().isOk());
    }

    @Test
    void publicConfigIsServedAndCachedAsJson() throws Exception {
        String key = "cache-test-" + UUID.randomUUID().toString().substring(0, 8);
        String value = "{\"testimonials\":[{\"name\":\"Ünïcode ✓\"}]}";
        save(adminToken(), key, value);

        // First read fills the cache, second is served from it; both must succeed and agree.
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(get("/api/v1/cms/config/" + key))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.configKey").value(key))
                    .andExpect(jsonPath("$.data.configValue").value(value));
        }

        String cached = redis.opsForValue().get("apsonic:cmsConfigs::" + key);
        assertThat(cached).as("entry stored in Redis as JSON").contains("\"configKey\"").contains(key);
        assertThat(redis.getExpire("apsonic:cmsConfigs::" + key)).as("entries expire").isPositive();
    }

    @Test
    void saving_evictsTheCachedCopySoTheNewContentIsVisibleImmediately() throws Exception {
        String key = "evict-test-" + UUID.randomUUID().toString().substring(0, 8);
        String token = adminToken();
        save(token, key, "{\"v\":1}");
        mockMvc.perform(get("/api/v1/cms/config/" + key)).andExpect(jsonPath("$.data.configValue").value("{\"v\":1}"));

        save(token, key, "{\"v\":2}");

        mockMvc.perform(get("/api/v1/cms/config/" + key)).andExpect(jsonPath("$.data.configValue").value("{\"v\":2}"));
    }

    @Test
    void anUnknownKeyIsNotFoundNotAServerError() throws Exception {
        mockMvc.perform(get("/api/v1/cms/config/no-such-key-" + UUID.randomUUID())).andExpect(status().isNotFound());
    }
}
