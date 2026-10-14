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
    void publishedCmsPageSectionsAreSanitizedAndPlainTextIsUntouched() throws Exception {
        String slug = "page-" + UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> request = Map.of(
                "slug", slug, "status", "PUBLISHED",
                "translations", Map.of("en", Map.of("title", "Fast & Furious")),
                "sections", List.of(Map.of("sectionType", "RICH_TEXT", "sectionOrder", 0,
                        "configuration", Map.of("contentHtml", Map.of("en", EVIL), "heading", "Fast & Furious"))));
        mockMvc.perform(post("/api/v1/admin/pages").header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(request)))
                .andExpect(status().is2xxSuccessful());

        JsonNode page = objectMapper.readTree(mockMvc.perform(get("/api/v1/pages/slug/" + slug)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).at("/data");

        JsonNode config = page.at("/publishedSnapshot/sections/0/configuration");
        assertClean(config.at("/contentHtml/en").asText());
        assertThat(config.at("/heading").asText()).isEqualTo("Fast & Furious");
        assertThat(page.at("/publishedSnapshot/translations/en/title").asText()).isEqualTo("Fast & Furious");

        String stored = jdbc.queryForObject("SELECT configuration::text FROM cms_page_sections s JOIN cms_pages p ON p.id = s.page_id WHERE p.slug = ?", String.class, slug);
        assertThat(stored.toLowerCase()).doesNotContain("<script", "onerror", "javascript:");
    }
}
