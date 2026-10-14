package com.apsonic.backend.shared.controller;

import com.apsonic.backend.BaseIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MediaUploadIntegrationTest extends BaseIntegrationTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D, 'I', 'H', 'D', 'R'};
    private static final byte[] SVG_XSS = "<svg xmlns=\"http://www.w3.org/2000/svg\" onload=\"alert(1)\"/>".getBytes(StandardCharsets.UTF_8);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    private final List<Path> created = new ArrayList<>();

    @AfterEach
    void cleanUp() throws Exception {
        for (Path p : created) {
            Files.deleteIfExists(p);
        }
    }

    private String adminToken() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "usernameOrEmail", BOOTSTRAP_ADMIN_USERNAME, "password", BOOTSTRAP_ADMIN_PASSWORD));
        String response = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(response);
        return json.at("/data/accessToken").asText();
    }

    @Test
    void anonymousUploadIsRejected() throws Exception {
        mockMvc.perform(multipart("/api/v1/admin/media/upload")
                        .file(new MockMultipartFile("file", "a.png", "image/png", PNG)))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void svgWithScriptIsRejected() throws Exception {
        mockMvc.perform(multipart("/api/v1/admin/media/upload")
                        .file(new MockMultipartFile("file", "x.svg", "image/svg+xml", SVG_XSS))
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void htmlDisguisedAsPngIsRejected() throws Exception {
        mockMvc.perform(multipart("/api/v1/admin/media/upload")
                        .file(new MockMultipartFile("file", "x.png", "image/png", "<script>1</script>".getBytes(StandardCharsets.UTF_8)))
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void validPngIsStoredWithServerChosenNameAndServedHardened() throws Exception {
        String response = mockMvc.perform(multipart("/api/v1/admin/media/upload")
                        .file(new MockMultipartFile("file", "../../My Photo.PNG", "image/png", PNG))
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String url = objectMapper.readTree(response).at("/data/url").asText();
        assertThat(url).matches("/uploads/[0-9a-f-]{36}\\.png");
        created.add(Paths.get("uploads").toAbsolutePath().resolve(url.substring("/uploads/".length())));

        mockMvc.perform(get(url))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Security-Policy", org.hamcrest.Matchers.containsString("sandbox")));
    }
}
