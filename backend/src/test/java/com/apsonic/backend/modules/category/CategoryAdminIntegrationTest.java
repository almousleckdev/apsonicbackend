package com.apsonic.backend.modules.category;

import com.apsonic.backend.BaseIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Admin category management: input validation and slug reuse after a soft delete. */
class CategoryAdminIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    private String token() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "usernameOrEmail", BOOTSTRAP_ADMIN_USERNAME, "password", BOOTSTRAP_ADMIN_PASSWORD));
        String response = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).at("/data/accessToken").asText();
    }

    private static String uniqueSlug() {
        return "t-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private Map<String, Object> category(String slug) {
        Map<String, Object> body = new HashMap<>();
        body.put("slug", slug);
        body.put("nameEn", "Test " + slug);
        body.put("parentType", "two-wheeler");
        body.put("brand", "apsonic");
        return body;
    }

    private ResultActions create(String token, Map<String, Object> body) throws Exception {
        return mockMvc.perform(post("/api/v1/admin/categories").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)));
    }

    private long createOk(String token, Map<String, Object> body) throws Exception {
        String response = create(token, body).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).at("/data/id").asLong();
    }

    @Test
    void createsACategoryWithImageAndGroup() throws Exception {
        Map<String, Object> body = category(uniqueSlug());
        body.put("parentType", "three-wheeler");
        body.put("imageUrl", "/uploads/cover.png");
        create(token(), body).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.parentType").value("three-wheeler"))
                .andExpect(jsonPath("$.data.imageUrl").value("/uploads/cover.png"));
    }

    @Test
    void duplicateSlugIsAConflict() throws Exception {
        String token = token();
        String slug = uniqueSlug();
        createOk(token, category(slug));
        create(token, category(slug)).andExpect(status().isConflict());
    }

    @Test
    void slugOfADeletedCategoryReportsAConflictInsteadOfAServerError() throws Exception {
        String token = token();
        String slug = uniqueSlug();
        long id = createOk(token, category(slug));
        mockMvc.perform(delete("/api/v1/admin/categories/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // The database keeps the deleted row (and its unique slug), so the slug is still taken.
        create(token, category(slug)).andExpect(status().isConflict());
    }

    @Test
    void rejectsMalformedGroupBrandSlugAndImage() throws Exception {
        String token = token();
        Map<String, Object> badGroup = category(uniqueSlug());
        badGroup.put("parentType", "Two Wheeler!");
        create(token, badGroup).andExpect(status().isBadRequest());

        Map<String, Object> badBrand = category(uniqueSlug());
        badBrand.put("brand", "<b>x</b>");
        create(token, badBrand).andExpect(status().isBadRequest());

        create(token, category("bad slug/..")).andExpect(status().isBadRequest());

        Map<String, Object> badImage = category(uniqueSlug());
        badImage.put("imageUrl", "javascript:alert(1)");
        create(token, badImage).andExpect(status().isBadRequest());

        Map<String, Object> protocolRelative = category(uniqueSlug());
        protocolRelative.put("imageUrl", "//evil.example/x.png");
        create(token, protocolRelative).andExpect(status().isBadRequest());
    }

    @Test
    void updateRejectsABlankEnglishName() throws Exception {
        String token = token();
        long id = createOk(token, category(uniqueSlug()));
        mockMvc.perform(put("/api/v1/admin/categories/" + id).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nameEn\":\"   \"}"))
                .andExpect(status().isBadRequest());
    }
}
