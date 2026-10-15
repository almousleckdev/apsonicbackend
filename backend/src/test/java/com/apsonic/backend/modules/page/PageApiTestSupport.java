package com.apsonic.backend.modules.page;

import com.apsonic.backend.BaseIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Shared helpers for the page builder and navigation API tests. */
public abstract class PageApiTestSupport extends BaseIntegrationTest {

    protected static final String EDITOR_PASSWORD = "Edit0r!Passw0rd#2026";

    @Autowired
    protected MockMvc mockMvc;
    @Autowired
    protected ObjectMapper mapper;

    private String adminToken;
    private String editorToken;

    protected String admin() throws Exception {
        if (adminToken == null) {
            adminToken = login(BOOTSTRAP_ADMIN_USERNAME, BOOTSTRAP_ADMIN_PASSWORD);
        }
        return adminToken;
    }

    /** A user whose only role is ROLE_EDITOR (read/write pages, no publish/delete/restore/navigation). */
    protected String editor() throws Exception {
        if (editorToken == null) {
            String username = "ed-" + UUID.randomUUID().toString().substring(0, 8);
            mockMvc.perform(post("/api/v1/admin/users").header("Authorization", "Bearer " + admin())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(Map.of("username", username, "email", username + "@apsonic.test",
                                    "password", EDITOR_PASSWORD, "roles", List.of("ROLE_EDITOR")))))
                    .andExpect(status().is2xxSuccessful());
            editorToken = login(username, EDITOR_PASSWORD);
        }
        return editorToken;
    }

    private String login(String user, String password) throws Exception {
        String body = mapper.writeValueAsString(Map.of("usernameOrEmail", user, "password", password));
        String response = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(response).at("/data/accessToken").asText();
    }

    protected static String slug() {
        return "t-" + UUID.randomUUID().toString().substring(0, 8);
    }

    // --- documents ---------------------------------------------------------------------------------------------

    protected ObjectNode document(String englishTitle, JsonNode... sections) {
        ObjectNode doc = mapper.createObjectNode();
        doc.put("schemaVersion", 1);
        doc.putObject("meta").putObject("en").put("title", englishTitle);
        doc.putObject("seo").put("noIndex", false);
        ArrayNode array = doc.putArray("sections");
        for (JsonNode section : sections) {
            array.add(section);
        }
        return doc;
    }

    protected ObjectNode hero(String heading) {
        ObjectNode section = mapper.createObjectNode();
        section.put("type", "hero");
        section.putObject("content").putObject("heading").put("en", heading);
        return section;
    }

    protected ObjectNode richText(String html) {
        ObjectNode section = mapper.createObjectNode();
        section.put("type", "rich-text");
        section.putObject("content").putObject("body").put("en", html);
        return section;
    }

    protected ObjectNode spacer() {
        ObjectNode section = mapper.createObjectNode();
        section.put("type", "spacer");
        section.putObject("content");
        return section;
    }

    // --- requests ----------------------------------------------------------------------------------------------

    protected ResultActions send(String method, String url, String token, Object body) throws Exception {
        var request = switch (method) {
            case "POST" -> post(url);
            case "PUT" -> put(url);
            case "DELETE" -> delete(url);
            default -> get(url);
        };
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body));
        }
        return mockMvc.perform(request);
    }

    protected JsonNode data(ResultActions actions) throws Exception {
        MvcResult result = actions.andReturn();
        return mapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    protected JsonNode createPage(String token, String slug, JsonNode document) throws Exception {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("slug", slug);
        body.put("title", document.at("/meta/en/title").asText("Untitled"));
        body.put("document", document);
        return data(send("POST", "/api/v1/admin/pages", token, body).andExpect(status().isCreated()));
    }

    protected JsonNode createPage(String slug) throws Exception {
        return createPage(admin(), slug, document("Page " + slug));
    }

    protected JsonNode saveDraft(String token, JsonNode page, JsonNode document) throws Exception {
        return data(trySave(token, page.path("id").asLong(), page.path("version").asLong(), null, document)
                .andExpect(status().isOk()));
    }

    protected ResultActions trySave(String token, long id, long version, String slug, JsonNode document) throws Exception {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("expectedVersion", version);
        if (slug != null) {
            body.put("slug", slug);
        }
        body.put("document", document);
        return send("PUT", "/api/v1/admin/pages/" + id, token, body);
    }

    protected ResultActions versioned(String action, String token, JsonNode page) throws Exception {
        return send("POST", "/api/v1/admin/pages/" + page.path("id").asLong() + "/" + action, token,
                Map.of("expectedVersion", page.path("version").asLong()));
    }

    protected JsonNode publish(JsonNode page) throws Exception {
        return data(versioned("publish", admin(), page).andExpect(status().isOk()));
    }

    protected JsonNode getAdmin(long id) throws Exception {
        return data(send("GET", "/api/v1/admin/pages/" + id, admin(), null).andExpect(status().isOk()));
    }

    protected ResultActions publicPage(String slug) throws Exception {
        return send("GET", "/api/v1/pages/slug/" + slug, null, null);
    }
}
