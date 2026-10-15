package com.apsonic.backend.modules.page.domain;

import com.apsonic.backend.shared.audit.BaseAuditableEntity;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * A CMS page. The draft and the published copy are separate documents: editing the draft never changes what the
 * public site serves; only an explicit publish copies the draft over the published document.
 */
@Entity
@Table(name = "cms_pages")
@SQLRestriction("deleted=false")
@Getter
@Setter
public class CmsPage extends BaseAuditableEntity {

    @Column(nullable = false, unique = true, length = 200)
    private String slug;

    /** English title, denormalised from the draft for lists and sorting. */
    @Column(nullable = false, length = 150)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private PageStatus status = PageStatus.DRAFT;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "draft_document", columnDefinition = "jsonb", nullable = false)
    private JsonNode draftDocument;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "published_document", columnDefinition = "jsonb")
    private JsonNode publishedDocument;

    @Column(name = "schema_version", nullable = false)
    private int schemaVersion = 1;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "published_by", length = 100)
    private String publishedBy;

    @Column(name = "archived_at")
    private Instant archivedAt;

    public boolean hasUnpublishedChanges() {
        return status == PageStatus.PUBLISHED && publishedDocument != null && !publishedDocument.equals(draftDocument);
    }

    /** True when the page has been live at some point, so its old URL may still be linked from elsewhere. */
    public boolean everPublished() {
        return publishedDocument != null;
    }
}
