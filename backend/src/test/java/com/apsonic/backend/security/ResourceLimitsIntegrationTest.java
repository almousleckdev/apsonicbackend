package com.apsonic.backend.security;

import com.apsonic.backend.BaseIntegrationTest;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles(profiles = "prod", inheritProfiles = false)
class ResourceLimitsIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private DataSource dataSource;
    @Autowired
    private MockMvc mockMvc;
    @Value("${server.tomcat.threads.max}")
    private int maxThreads;
    @Value("${server.tomcat.max-http-form-post-size}")
    private String formPostSize;

    @Test
    void connectionPoolAndThreadsAreSizedForASingleSmallInstance() throws Exception {
        HikariDataSource hikari = dataSource.unwrap(HikariDataSource.class);
        assertThat(hikari.getMaximumPoolSize()).isLessThanOrEqualTo(30);
        assertThat(hikari.getMinimumIdle()).isLessThanOrEqualTo(hikari.getMaximumPoolSize());
        assertThat(hikari.getLeakDetectionThreshold()).isPositive();
        assertThat(maxThreads).isLessThanOrEqualTo(200);
        assertThat(formPostSize).isEqualTo("2MB");
    }

    @Test
    void anonymousCannotUseTheUploadEndpoint() throws Exception {
        mockMvc.perform(multipart("/api/v1/admin/media/upload").file(new MockMultipartFile("file", "a.png", "image/png", new byte[10])))
                .andExpect(status().is4xxClientError());
    }
}
