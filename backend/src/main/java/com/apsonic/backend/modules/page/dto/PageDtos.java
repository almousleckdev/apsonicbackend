package com.apsonic.backend.modules.page.dto;

import com.apsonic.backend.modules.page.document.DocumentCheck;
import com.apsonic.backend.modules.page.document.DocumentIssue;
import com.apsonic.backend.modules.page.domain.PageStatus;
import com.apsonic.backend.modules.page.domain.RevisionKind;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Request and response shapes of the page API. Records keep them immutable and the controller thin. */
public final class PageDtos {

    private PageDtos() {
    }

    public record CreatePageRequest(
            @NotBlank(message = "Slug is required") @Size(max = 80, message = "Slug is too long") String slug,
            @NotBlank(message = "Title is required") @Size(max = 150, message = "Title is too long") String title,
            JsonNode document) {
    }

    /** {@code slug} is optional; when present and different it renames the page (see the slug rules). */
    public record SaveDraftRequest(
            @NotNull(message = "expectedVersion is required") Long expectedVersion,
            @Size(max = 80, message = "Slug is too long") String slug,
            @NotNull(message = "document is required") JsonNode document) {
    }

    public record VersionRequest(@NotNull(message = "expectedVersion is required") Long expectedVersion) {
    }

    public record PageSummary(
            Long id, String slug, String title, PageStatus status, boolean hasUnpublishedChanges, Long version,
            Instant createdAt, String createdBy, Instant updatedAt, String updatedBy, Instant publishedAt) {
    }

    public record PageDetail(
            Long id, String slug, String title, PageStatus status, boolean hasUnpublishedChanges, Long version,
            Instant createdAt, String createdBy, Instant updatedAt, String updatedBy,
            Instant publishedAt, String publishedBy, Instant archivedAt,
            JsonNode document) {
    }

    public record ValidationReport(boolean publishable, List<DocumentIssue> issues,
                                   Map<String, DocumentCheck.Completeness> completeness) {
    }

    public record RevisionSummary(int number, RevisionKind kind, String note, String createdBy, Instant savedAt) {
    }

    public record RevisionDetail(int number, RevisionKind kind, String note, String createdBy, Instant savedAt,
                                 JsonNode document) {
    }

    public record PreviewLink(String token, Instant expiresAt) {
    }

    /** What the public site renders. Either {@code document} or {@code redirectSlug} is set. */
    public record PublicPage(String slug, String redirectSlug, String title, JsonNode document, Instant publishedAt) {
    }

    /** Draft content for an editor-held preview token. Never served by public endpoints. */
    public record PreviewPage(Long id, String slug, String title, PageStatus status, JsonNode document) {
    }

    public record SectionTypesResponse(
            List<com.apsonic.backend.modules.page.document.SectionDefinition> sections,
            List<com.apsonic.backend.modules.page.document.FieldDef> styleFields,
            List<String> locales) {
    }

    public record SlugCheck(boolean available) {
    }
}
