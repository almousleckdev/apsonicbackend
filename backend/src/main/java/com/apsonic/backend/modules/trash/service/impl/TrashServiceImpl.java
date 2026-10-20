package com.apsonic.backend.modules.trash.service.impl;

import com.apsonic.backend.modules.navigation.service.NavigationService;
import com.apsonic.backend.modules.trash.dto.TrashItemResponse;
import com.apsonic.backend.modules.trash.service.TrashService;
import com.apsonic.backend.shared.exception.BadRequestException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
public class TrashServiceImpl implements TrashService {

    private final JdbcTemplate jdbcTemplate;

    private static final String NAVIGATION = "navigation";

    private final NavigationService navigationService;

    public TrashServiceImpl(JdbcTemplate jdbcTemplate, NavigationService navigationService) {
        this.jdbcTemplate = jdbcTemplate;
        this.navigationService = navigationService;
    }

    private String getTableName(String resourceType) {
        return switch (resourceType.toLowerCase()) {
            case "products" -> "products";
            case "categories" -> "categories";
            case "news" -> "news";
            case "users" -> "users";
            case "notifications", "contact" -> "contact_messages";
            case "pages", "cms_pages" -> "cms_pages";
            default -> throw new BadRequestException("Invalid resource type for trash");
        };
    }

    private String getTitleColumn(String resourceType) {
        return switch (resourceType.toLowerCase()) {
            case "products" -> "model_name";
            case "categories" -> "name_en";
            case "news" -> "title_en";
            case "users" -> "email";
            case "notifications", "contact" -> "subject";
            case "pages", "cms_pages" -> "title";
            default -> "id";
        };
    }

    @Override
    @Transactional(readOnly = true)
    public List<TrashItemResponse> getTrashItems(String resourceType) {
        if (NAVIGATION.equalsIgnoreCase(resourceType)) {
            return jdbcTemplate.query(
                    "SELECT id, menu_key, title, deleted_at, deleted_by FROM cms_navigation_trash ORDER BY deleted_at DESC",
                    (rs, rowNum) -> {
                        Timestamp ts = rs.getTimestamp("deleted_at");
                        String menu = "FOOTER".equals(rs.getString("menu_key")) ? "Footer" : "Header";
                        return TrashItemResponse.builder()
                                .id(rs.getString("id"))
                                .title(menu + " menu · " + rs.getString("title"))
                                .deletedAt(ts != null ? ts.toInstant() : null)
                                .deletedBy(rs.getString("deleted_by"))
                                .build();
                    });
        }
        String table = getTableName(resourceType);
        String titleCol = getTitleColumn(resourceType);

        String sql = String.format("SELECT id, %s as title, deleted_at, deleted_by FROM %s WHERE deleted = true ORDER BY deleted_at DESC", titleCol, table);

        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            Timestamp ts = rs.getTimestamp("deleted_at");
            Instant deletedAt = ts != null ? ts.toInstant() : null;
            return TrashItemResponse.builder()
                    .id(rs.getString("id"))
                    .title(rs.getString("title"))
                    .deletedAt(deletedAt)
                    .deletedBy(rs.getString("deleted_by"))
                    .build();
        });
    }

    @Override
    @Transactional
    public void restoreItem(String resourceType, String id) {
        if (NAVIGATION.equalsIgnoreCase(resourceType)) {
            navigationService.restoreTrashedItem(Long.parseLong(id));
            return;
        }
        String table = getTableName(resourceType);
        String sql = String.format("UPDATE %s SET deleted = false, deleted_at = null, deleted_by = null WHERE id = ?", table);
        jdbcTemplate.update(sql, Long.parseLong(id));
    }

    @Override
    @Transactional
    public void deletePermanently(String resourceType, String id) {
        if (NAVIGATION.equalsIgnoreCase(resourceType)) {
            jdbcTemplate.update("DELETE FROM cms_navigation_trash WHERE id = ?", Long.parseLong(id));
            return;
        }
        String table = getTableName(resourceType);
        String sql = String.format("DELETE FROM %s WHERE id = ? AND deleted = true", table);
        jdbcTemplate.update(sql, Long.parseLong(id));
    }

    @Override
    @Transactional
    public void clearTrash(String resourceType) {
        if (NAVIGATION.equalsIgnoreCase(resourceType)) {
            jdbcTemplate.update("DELETE FROM cms_navigation_trash");
            return;
        }
        String table = getTableName(resourceType);
        String sql = String.format("DELETE FROM %s WHERE deleted = true", table);
        jdbcTemplate.update(sql);
    }
}
