package com.apsonic.backend.modules.page.domain;

import com.apsonic.backend.modules.page.domain.enums.PageStatus;
import com.apsonic.backend.shared.audit.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.JsonNode;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "cms_pages")
@SQLRestriction("deleted=false")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CmsPage extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String slug;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PageStatus status;

    @Column(name = "nav_visibility", nullable = false)
    private boolean navVisibility;

    @Column(name = "nav_position")
    private String navPosition;

    @Column(name = "nav_order", nullable = false)
    private Integer navOrder;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "published_snapshot", columnDefinition = "jsonb")
    private JsonNode publishedSnapshot;

    @Column(name = "published_at")
    private java.time.Instant publishedAt;

    @OneToMany(mappedBy = "page", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sectionOrder ASC")
    @Builder.Default
    private List<CmsPageSection> sections = new ArrayList<>();

    @OneToMany(mappedBy = "page", cascade = CascadeType.ALL, orphanRemoval = true)
    @MapKey(name = "locale")
    @Builder.Default
    private Map<String, CmsPageTranslation> translations = new HashMap<>();

    public void addSection(CmsPageSection section) {
        sections.add(section);
        section.setPage(this);
    }

    public void removeSection(CmsPageSection section) {
        sections.remove(section);
        section.setPage(null);
    }

    public void addTranslation(CmsPageTranslation translation) {
        translations.put(translation.getLocale(), translation);
        translation.setPage(this);
    }

    public void removeTranslation(String locale) {
        CmsPageTranslation translation = translations.remove(locale);
        if (translation != null) {
            translation.setPage(null);
        }
    }
}
