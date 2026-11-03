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

    private static byte[] bigPhotoPng() throws Exception {
        var img = new java.awt.image.BufferedImage(3000, 2000, java.awt.image.BufferedImage.TYPE_INT_RGB);
        var rnd = new java.util.Random(1);
        for (int y = 0; y < 2000; y++) {
            for (int x = 0; x < 3000; x++) {
                img.setRGB(x, y, ((x * 255 / 3000 + rnd.nextInt(20)) & 0xFF) << 16 | ((y * 255 / 2000 + rnd.nextInt(20)) & 0xFF) << 8 | rnd.nextInt(256) & 0x3F);
            }
        }
        var out = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    @Test
    void largePhotoIsShrunkBeforeItIsStored() throws Exception {
        byte[] original = bigPhotoPng();
        String response = mockMvc.perform(multipart("/api/v1/admin/media/upload")
                        .file(new MockMultipartFile("file", "photo.png", "image/png", original))
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String url = objectMapper.readTree(response).at("/data/url").asText();
        Path stored = Paths.get("uploads").toAbsolutePath().resolve(url.substring("/uploads/".length()));
        created.add(stored);

        // An opaque photo PNG is stored as a much smaller JPEG no wider than 1920px.
        assertThat(url).endsWith(".jpg");
        assertThat(Files.size(stored)).isLessThan(original.length / 3);
        var decoded = javax.imageio.ImageIO.read(stored.toFile());
        assertThat(decoded.getWidth()).isEqualTo(1920);
    }
}
