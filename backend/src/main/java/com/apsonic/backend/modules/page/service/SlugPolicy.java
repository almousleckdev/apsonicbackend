package com.apsonic.backend.modules.page.service;

import com.apsonic.backend.modules.page.repository.CmsPageRepository;
import com.apsonic.backend.modules.page.repository.CmsPageSlugHistoryRepository;
import com.apsonic.backend.shared.exception.BadRequestException;
import com.apsonic.backend.shared.exception.DuplicateResourceException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.regex.Pattern;

/** The rules for page URLs: shape, reserved words and uniqueness (including deleted pages and former slugs). */
@Component
@RequiredArgsConstructor
public class SlugPolicy {

    public static final int MAX_LENGTH = 80;
    private static final Pattern SHAPE = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*$");

    /** Top-level paths that already belong to the application. */
    private static final Set<String> RESERVED = Set.of(
            "admin", "api", "preview", "products", "product", "services", "contact", "news", "login", "logout",
            "uploads", "categories", "category", "search", "sitemap", "robots", "favicon", "_next", "static",
            "public", "en", "fr", "zh");

    private final CmsPageRepository pages;
    private final CmsPageSlugHistoryRepository history;

    /** Shape and reserved-word checks only. */
    public void assertValid(String slug) {
        if (slug == null || slug.isBlank()) {
            throw new BadRequestException("The page address (slug) is required");
        }
        if (slug.length() > MAX_LENGTH || !SHAPE.matcher(slug).matches()) {
            throw new BadRequestException("The page address may only contain lowercase letters, numbers and single "
                    + "hyphens, up to " + MAX_LENGTH + " characters");
        }
        if (RESERVED.contains(slug)) {
            throw new BadRequestException("'" + slug + "' is reserved by the website and cannot be used as a page address");
        }
    }

    /** Validity plus uniqueness. {@code ownPageId} is the page being edited, which may keep its own slug. */
    public void assertAvailable(String slug, Long ownPageId) {
        assertValid(slug);
        if (pages.slugTakenIncludingDeleted(slug, ownPageId)
                || (ownPageId == null ? history.findBySlug(slug).isPresent() : history.existsBySlugAndPageIdNot(slug, ownPageId))) {
            throw new DuplicateResourceException("A page with the address '" + slug + "' already exists (or used to)");
        }
    }

    public boolean isAvailable(String slug, Long ownPageId) {
        try {
            assertAvailable(slug, ownPageId);
            return true;
        } catch (BadRequestException | DuplicateResourceException e) {
            return false;
        }
    }
}
