package com.apsonic.backend.security;

import com.apsonic.backend.BaseIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Stored XSS through CMS content, checked through the real API and database. */
class HtmlSanitizationIntegrationTest extends BaseIntegrationTest {

    private static final String EVIL = "<p>Hello</p><script>alert(document.domain)</script>"
            + "<img src=x onerror=alert(1)><a href=\"javascript:alert(1)\">go</a>";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbc;

    private String adminToken() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "usernameOrEmail", BOOTSTRAP_ADMIN_USERNAME, "password", BOOTSTRAP_ADMIN_PASSWORD));
        String response = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).at("/data/accessToken").asText();
    }

    private static void assertClean(String html) {
        assertThat(html).contains("<p>Hello</p>");
        assertThat(html.toLowerCase()).doesNotContain("<script", "onerror", "javascript:", "alert(");
    }

    @Test
    void newsArticleSavedByAnEditorIsStoredAndServedSanitized() throws Exception {
        String slug = "xss-" + UUID.randomUUID().toString().substring(0, 8);
        String body = objectMapper.writeValueAsString(Map.of("slug", slug, "titleEn", "t", "category", "Corporate",
                "published", true, "contentHtmlEn", EVIL, "contentHtmlFr", EVIL));
        mockMvc.perform(post("/api/v1/admin/news").header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is2xxSuccessful());

        assertClean(jdbc.queryForObject("SELECT content_html_en FROM news WHERE slug = ?", String.class, slug));

        JsonNode served = objectMapper.readTree(mockMvc.perform(get("/api/v1/news/" + slug)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).at("/data");
        assertClean(served.get("contentHtml").asText());
        assertClean(served.get("contentHtmlEn").asText());
        assertClean(served.get("contentHtmlFr").asText());
    }

    @Test
    void legacyUnsafeRowsAlreadyInTheDatabaseAreSanitizedOnRead() throws Exception {
        String slug = "legacy-" + UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("INSERT INTO news (slug, title_en, category, content_html_en) VALUES (?, 't', 'Corporate', ?)", slug, EVIL);
        assertThat(jdbc.queryForObject("SELECT content_html_en FROM news WHERE slug = ?", String.class, slug))
                .contains("<script>");

        JsonNode served = objectMapper.readTree(mockMvc.perform(get("/api/v1/news/" + slug)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).at("/data");

        assertClean(served.get("contentHtml").asText());
    }

    @Test
    void pageContentStoredBeforeThePolicyExistedIsSanitizedWhenServed() throws Exception {
        // A row written directly to the database, bypassing every save-time check (old data, manual edits, a bug).
        String slug = "page-" + UUID.randomUUID().toString().substring(0, 8);
        String document = objectMapper.writeValueAsString(Map.of("schemaVersion", 1,
                "meta", Map.of("en", Map.of("title", "Fast & Furious")),
                "sections", List.of(Map.of("id", UUID.randomUUID().toString(), "type", "rich-text", "hidden", false,
                        "content", Map.of("body", Map.of("en", EVIL))))));
        jdbc.update("INSERT INTO cms_pages (slug, title, status, draft_document, published_document, published_at) "
                + "VALUES (?, 'Fast & Furious', 'PUBLISHED', ?::jsonb, ?::jsonb, now())", slug, document, document);

        JsonNode page = objectMapper.readTree(mockMvc.perform(get("/api/v1/pages/slug/" + slug)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).at("/data");

        assertClean(page.at("/document/sections/0/content/body/en").asText());
        // Plain text is never entity-encoded.
        assertThat(page.at("/document/meta/en/title").asText()).isEqualTo("Fast & Furious");
        assertThat(page.at("/title").asText()).isEqualTo("Fast & Furious");
    }
}
