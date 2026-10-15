package com.apsonic.backend.modules.navigation.dto;

import com.apsonic.backend.modules.page.domain.PageStatus;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class NavigationDtos {

    private NavigationDtos() {
    }

    public record SaveMenuRequest(
            @NotNull(message = "expectedVersion is required") Long expectedVersion,
            @NotNull(message = "items is required") JsonNode items) {
    }

    public record VersionRequest(@NotNull(message = "expectedVersion is required") Long expectedVersion) {
    }

    /** Both copies of a menu, so the editor can show "unpublished changes" and offer to discard them. */
    public record MenuView(String menu, Long version, JsonNode draft, JsonNode published, boolean hasUnpublishedChanges,
                           boolean configured, Instant publishedAt, String publishedBy) {
    }

    /** A page that can be added to a menu. */
    public record PageOption(Long id, String slug, String title, PageStatus status) {
    }

    /** A menu item as the site renders it: page references already turned into the page's current address. */
    public record ResolvedItem(String id, String kind, Map<String, String> labels, String href, boolean openInNewTab,
                               boolean pending, List<ResolvedItem> children) {
    }

    /** {@code configured} is false until the menu was first published: the site then keeps its built-in navigation. */
    public record ResolvedMenu(String menu, boolean configured, List<ResolvedItem> items) {
    }
}
