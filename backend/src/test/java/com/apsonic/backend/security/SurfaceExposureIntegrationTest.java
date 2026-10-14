package com.apsonic.backend.security;

import com.apsonic.backend.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** What an anonymous visitor can reach: production must expose the health probe and nothing diagnostic. */
class SurfaceExposureIntegrationTest {

    @ActiveProfiles(profiles = "prod", inheritProfiles = false)
    static class Prod extends BaseIntegrationTest {

        @Autowired
        MockMvc mockMvc;

        @Test
        void apiDocsAndSwaggerAreNotReachable() throws Exception {
            for (String path : new String[]{"/v3/api-docs", "/v3/api-docs/swagger-config", "/swagger-ui.html", "/swagger-ui/index.html"}) {
                mockMvc.perform(get(path)).andExpect(status().is4xxClientError());
            }
        }

        @Test
        void diagnosticActuatorEndpointsAreNotReachable() throws Exception {
            for (String path : new String[]{"/actuator", "/actuator/metrics", "/actuator/prometheus", "/actuator/env",
                    "/actuator/heapdump", "/actuator/beans", "/actuator/info"}) {
                mockMvc.perform(get(path)).andExpect(status().is4xxClientError());
            }
        }

        @Test
        void healthProbeIsPublicButRevealsNoComponentDetail() throws Exception {
            mockMvc.perform(get("/actuator/health"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("UP")))
                    .andExpect(content().string(not(containsString("postgres"))))
                    .andExpect(content().string(not(containsString("components"))))
                    .andExpect(content().string(not(containsString("diskSpace"))));
        }
    }

    @ActiveProfiles(profiles = "local", inheritProfiles = false)
    static class Local extends BaseIntegrationTest {

        @Autowired
        MockMvc mockMvc;

        @Test
        void localDevelopmentKeepsApiDocs() throws Exception {
            mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
        }
    }
}
