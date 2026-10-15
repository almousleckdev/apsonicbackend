package com.apsonic.backend;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;
import org.testcontainers.junit.jupiter.Container;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("local")
// Most tests log in many times from one address; RateLimitIntegrationTest overrides this to true.
@org.springframework.test.context.TestPropertySource(properties = "app.rate-limit.enabled=false")
public abstract class BaseIntegrationTest {

    @Container
    protected static final PostgreSQLContainer<?> postgresContainer = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("apsonic_cms_test")
            .withUsername("test_user")
            .withPassword("test_password");

    protected static final String REDIS_PASSWORD = "it-redis-" + UUID.randomUUID();
    protected static final GenericContainer<?> redisContainer = new GenericContainer<>("redis:7-alpine")
            .withCommand("redis-server", "--requirepass", REDIS_PASSWORD)
            .withExposedPorts(6379);

    protected static final String ALLOWED_ORIGIN = "https://www.apsonic.test";
    protected static final String BOOTSTRAP_ADMIN_USERNAME = "it-owner";
    protected static final String BOOTSTRAP_ADMIN_PASSWORD = "It-Bootstrap!Pass#" + UUID.randomUUID();

    static {
        postgresContainer.start();
        redisContainer.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgresContainer::getJdbcUrl);
        registry.add("spring.datasource.username", postgresContainer::getUsername);
        registry.add("spring.datasource.password", postgresContainer::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.data.redis.host", redisContainer::getHost);
        registry.add("spring.data.redis.port", () -> redisContainer.getMappedPort(6379));
        registry.add("spring.data.redis.password", () -> REDIS_PASSWORD);
        // Dynamic properties take precedence over any developer .env, so tests never depend on local secrets.
        registry.add("app.security.jwt.secret", () -> {
            byte[] key = new byte[48];
            new SecureRandom().nextBytes(key);
            return Base64.getEncoder().encodeToString(key);
        });
        registry.add("app.security.jwt.expiration-ms", () -> "900000");
        // Production refuses to start without a preview signing key, so the prod-profile tests need one too.
        registry.add("app.preview.secret", () -> {
            byte[] key = new byte[48];
            new SecureRandom().nextBytes(key);
            return Base64.getEncoder().encodeToString(key);
        });
        registry.add("app.cors.allowed-origins", () -> ALLOWED_ORIGIN);
        registry.add("app.bootstrap-admin.username", () -> BOOTSTRAP_ADMIN_USERNAME);
        registry.add("app.bootstrap-admin.email", () -> "it-owner@apsonic.test");
        registry.add("app.bootstrap-admin.password", () -> BOOTSTRAP_ADMIN_PASSWORD);
    }
}
