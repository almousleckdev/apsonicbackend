package com.apsonic.backend.modules.auth;

import com.apsonic.backend.BaseIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Creating an account must never end in a 500: duplicates are a clear 409, and deleted accounts free their email. */
class DeletedAccountReuseIntegrationTest extends BaseIntegrationTest {

    private static final String PASSWORD = "Strong#Pass-1234";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper mapper;

    private String adminToken() throws Exception {
        String body = mapper.writeValueAsString(Map.of("usernameOrEmail", BOOTSTRAP_ADMIN_USERNAME, "password", BOOTSTRAP_ADMIN_PASSWORD));
        String response = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(response).at("/data/accessToken").asText();
    }

    private MvcResult create(String token, String username, String email) throws Exception {
        return mockMvc.perform(post("/api/v1/admin/users").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("username", username, "email", email,
                        "password", PASSWORD, "roles", List.of("ROLE_EDITOR"))))).andReturn();
    }

    @Test
    void anActiveDuplicateIsAClearConflictNotAServerError() throws Exception {
        String token = adminToken();
        String name = "dup-" + UUID.randomUUID().toString().substring(0, 8);
        assertThat(create(token, name, name + "@apsonic.test").getResponse().getStatus()).isEqualTo(201);

        mockMvc.perform(post("/api/v1/admin/users").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("username", name + "x", "email", name + "@apsonic.test",
                                "password", PASSWORD, "roles", List.of("ROLE_EDITOR")))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("An account with this email address already exists."));
    }

    @Test
    void aDeletedAccountNoLongerBlocksItsEmailOrUsername() throws Exception {
        String token = adminToken();
        String name = "gone-" + UUID.randomUUID().toString().substring(0, 8);
        MvcResult first = create(token, name, name + "@apsonic.test");
        assertThat(first.getResponse().getStatus()).isEqualTo(201);
        JsonNode created = mapper.readTree(first.getResponse().getContentAsString());
        long id = created.at("/data/id").asLong();

        mockMvc.perform(delete("/api/v1/admin/users/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().is2xxSuccessful());

        // Same username and email again: this used to fail with an unexpected internal error.
        assertThat(create(token, name, name + "@apsonic.test").getResponse().getStatus()).isEqualTo(201);
    }
}
