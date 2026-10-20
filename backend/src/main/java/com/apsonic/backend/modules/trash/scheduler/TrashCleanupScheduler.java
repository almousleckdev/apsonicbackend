package com.apsonic.backend.modules.trash.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TrashCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(TrashCleanupScheduler.class);
    private final JdbcTemplate jdbcTemplate;

    public TrashCleanupScheduler(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Scheduled(cron = "0 0 2 * * ?") // Run at 2 AM every day
    public void cleanupOldTrashItems() {
        log.info("Starting automatic trash cleanup for items older than 3 months");
        String[] tables = {"products", "categories", "news", "users", "contact_messages", "cms_pages"};
        for (String table : tables) {
            try {
                String sql = String.format("DELETE FROM %s WHERE deleted = true AND deleted_at < NOW() - INTERVAL '3 months'", table);
                int deleted = jdbcTemplate.update(sql);
                if (deleted > 0) {
                    log.info("Automatically deleted {} old trash items from table {}", deleted, table);
                }
            } catch (Exception e) {
                log.error("Failed to cleanup trash for table " + table, e);
            }
        }
        try {
            jdbcTemplate.update("DELETE FROM cms_navigation_trash WHERE deleted_at < NOW() - INTERVAL '3 months'");
        } catch (Exception e) {
            log.error("Failed to cleanup trash for navigation items", e);
        }
        log.info("Finished automatic trash cleanup");
    }
}
