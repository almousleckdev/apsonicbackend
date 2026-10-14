package com.apsonic.backend.security;

import com.apsonic.backend.BaseIntegrationTest;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.exception.FlywayValidateException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Production migrations are strict: drift must stop the application, never be ignored. */
@ActiveProfiles(profiles = "prod", inheritProfiles = false)
class FlywayStrictnessIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private Flyway flyway;
    @Autowired
    private JdbcTemplate jdbc;
    @Value("${spring.flyway.validate-on-migrate}")
    private boolean validateOnMigrate;
    @Value("${spring.flyway.baseline-on-migrate}")
    private boolean baselineOnMigrate;

    private Integer originalChecksum;

    @AfterEach
    void restoreChecksum() {
        if (originalChecksum != null) {
            jdbc.update("UPDATE flyway_schema_history SET checksum = ? WHERE version = '1'", originalChecksum);
        }
    }

    @Test
    void productionRunsFlywayStrictly() {
        assertThat(validateOnMigrate).isTrue();
        assertThat(baselineOnMigrate).isFalse();
    }

    @Test
    void theFullMigrationSetValidatesCleanlyOnAFreshDatabase() {
        flyway.validate();
        assertThat(flyway.info().pending()).isEmpty();
    }

    @Test
    void anEditedAppliedMigrationIsDetectedAndBlocksMigration() {
        originalChecksum = jdbc.queryForObject("SELECT checksum FROM flyway_schema_history WHERE version = '1'", Integer.class);
        jdbc.update("UPDATE flyway_schema_history SET checksum = checksum + 1 WHERE version = '1'");

        assertThatThrownBy(() -> flyway.migrate()).isInstanceOf(FlywayValidateException.class);
    }
}
