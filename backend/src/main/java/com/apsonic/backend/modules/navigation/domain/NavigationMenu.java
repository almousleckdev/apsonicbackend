package com.apsonic.backend.modules.navigation.domain;

import com.apsonic.backend.shared.audit.BaseAuditableEntity;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/** One menu (header or footer) with a draft tree that can be previewed and a published tree the site serves. */
@Entity
@Table(name = "cms_navigation_menus")
@SQLRestriction("deleted=false")
@Getter
@Setter
public class NavigationMenu extends BaseAuditableEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "menu_key", nullable = false, unique = true, length = 20)
    private MenuKey menuKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "draft_items", columnDefinition = "jsonb", nullable = false)
    private JsonNode draftItems;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "published_items", columnDefinition = "jsonb", nullable = false)
    private JsonNode publishedItems;

    /** Null until an administrator first publishes the menu; until then the site keeps its built-in navigation. */
    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "published_by", length = 100)
    private String publishedBy;
}
