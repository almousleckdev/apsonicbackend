package com.apsonic.backend.modules.page.service;

import com.apsonic.backend.modules.page.document.DocumentCheck;
import com.apsonic.backend.modules.page.document.DocumentIssue;
import com.apsonic.backend.modules.page.document.DocumentValidator;
import com.apsonic.backend.modules.page.document.DocumentValidator.Mode;
import com.apsonic.backend.modules.page.domain.CmsPage;
import com.apsonic.backend.modules.page.domain.CmsPageRevision;
import com.apsonic.backend.modules.page.domain.CmsPageSlugHistory;
import com.apsonic.backend.modules.page.domain.PageStatus;
import com.apsonic.backend.modules.page.domain.RevisionKind;
import com.apsonic.backend.modules.page.dto.PageDtos.CreatePageRequest;
import com.apsonic.backend.modules.page.dto.PageDtos.PageDetail;
import com.apsonic.backend.modules.page.dto.PageDtos.SaveDraftRequest;
import com.apsonic.backend.modules.page.repository.CmsPageRepository;
import com.apsonic.backend.modules.page.repository.CmsPageRevisionRepository;
import com.apsonic.backend.modules.page.repository.CmsPageSlugHistoryRepository;
import com.apsonic.backend.shared.api.ValidationError;
import com.apsonic.backend.shared.exception.ConflictException;
import com.apsonic.backend.shared.exception.DuplicateResourceException;
import com.apsonic.backend.shared.exception.ResourceNotFoundException;
import com.apsonic.backend.shared.exception.ValidationFailedException;
import com.apsonic.backend.shared.util.SecurityUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Every state change of a page. Rules enforced here (the controller only checks permissions):
 * saving a draft never publishes; writes carry the version the editor last saw and are refused when it is stale;
 * archived pages are read-only until restored; published pages cannot be deleted or archived while the navigation
 * still points at them.
 */
@Service
@RequiredArgsConstructor
public class PageCommandService {

    private final CmsPageRepository pages;
    private final CmsPageRevisionRepository revisions;
    private final CmsPageSlugHistoryRepository slugHistory;
    private final DocumentValidator validator;
    private final SlugPolicy slugPolicy;
    private final PageRevisionRecorder recorder;
    private final PageReferenceGuard referenceGuard;
    private final PageMapper mapper;

    @Transactional
    public PageDetail create(CreatePageRequest request) {
        String slug = request.slug().strip();
        slugPolicy.assertAvailable(slug, null);

        JsonNode input = request.document();
        if (input == null || input.isNull()) {
            input = validator.emptyDocument(request.title().strip());
        }
        DocumentCheck check = checkForSave(input);

        CmsPage page = new CmsPage();
        page.setSlug(slug);
        page.setStatus(PageStatus.DRAFT);
        applyDraft(page, check.document(), request.title().strip());
        page = saveUnique(page, slug);

        recorder.record(page, RevisionKind.CREATE, null, page.getDraftDocument());
        return mapper.detail(page);
    }

    @Transactional
    public PageDetail saveDraft(Long id, SaveDraftRequest request) {
        CmsPage page = load(id);
        assertEditable(page);
        assertVersion(page, request.expectedVersion());

        DocumentCheck check = checkForSave(request.document());
        String newSlug = request.slug() == null ? page.getSlug() : request.slug().strip();
        boolean slugChanged = !newSlug.equals(page.getSlug());
        boolean documentChanged = !check.document().equals(page.getDraftDocument());
        if (!slugChanged && !documentChanged) {
            return mapper.detail(page);
        }

        if (slugChanged) {
            renameSlug(page, newSlug);
        }
        if (documentChanged) {
            applyDraft(page, check.document(), null);
        }
        page = saveUnique(page, newSlug);

        recorder.record(page, RevisionKind.DRAFT, slugChanged ? "Address changed to /" + newSlug : null, page.getDraftDocument());
        return mapper.detail(page);
    }

    /** Copies the draft to the public copy after the strict (publish-mode) check. Requires PUBLISH_PAGES (controller). */
    @Transactional
    public PageDetail publish(Long id, Long expectedVersion) {
        CmsPage page = load(id);
        assertEditable(page);
        assertVersion(page, expectedVersion);

        DocumentCheck check = validator.check(page.getDraftDocument(), Mode.PUBLISH);
        if (check.hasErrors()) {
            throw invalid("The page cannot be published yet", check.errors());
        }
        page.setDraftDocument(check.document());
        page.setPublishedDocument(check.document());
        page.setStatus(PageStatus.PUBLISHED);
        page.setPublishedAt(Instant.now());
        page.setPublishedBy(SecurityUtils.getCurrentUsername().orElse("system"));
        page.setArchivedAt(null);
        page = pages.saveAndFlush(page);

        recorder.record(page, RevisionKind.PUBLISH, null, check.document());
        return mapper.detail(page);
    }

    @Transactional
    public PageDetail unpublish(Long id, Long expectedVersion) {
        CmsPage page = load(id);
        assertVersion(page, expectedVersion);
        if (page.getStatus() != PageStatus.PUBLISHED) {
            throw new ConflictException("Only a published page can be unpublished");
        }
        page.setStatus(PageStatus.DRAFT);
        return mapper.detail(pages.saveAndFlush(page));
    }

    @Transactional
    public PageDetail archive(Long id, Long expectedVersion) {
        CmsPage page = load(id);
        assertVersion(page, expectedVersion);
        if (page.getStatus() == PageStatus.ARCHIVED) {
            throw new ConflictException("The page is already archived");
        }
        assertNotReferenced(page, "archived");
        page.setStatus(PageStatus.ARCHIVED);
        page.setArchivedAt(Instant.now());
        return mapper.detail(pages.saveAndFlush(page));
    }

    @Transactional
    public PageDetail restore(Long id, Long expectedVersion) {
        CmsPage page = load(id);
        assertVersion(page, expectedVersion);
        if (page.getStatus() != PageStatus.ARCHIVED) {
            throw new ConflictException("Only an archived page can be restored");
        }
        page.setStatus(PageStatus.DRAFT);
        page.setArchivedAt(null);
        return mapper.detail(pages.saveAndFlush(page));
    }

    @Transactional
    public PageDetail duplicate(Long id) {
        CmsPage source = load(id);
        String slug = freeCopySlug(source.getSlug());

        ObjectNode copy = source.getDraftDocument().deepCopy();
        ArrayNode sections = (ArrayNode) copy.path("sections");
        sections.forEach(section -> ((ObjectNode) section).put("id", UUID.randomUUID().toString()));
        String title = truncate(source.getTitle() + " (copy)", 150);
        if (copy.path("meta").path("en").isObject()) {
            ((ObjectNode) copy.path("meta").path("en")).put("title", title);
        }

        CmsPage page = new CmsPage();
        page.setSlug(slug);
        page.setStatus(PageStatus.DRAFT);
        applyDraft(page, validator.check(copy, Mode.DRAFT).document(), title);
        page = saveUnique(page, slug);

        recorder.record(page, RevisionKind.CREATE, "Duplicated from /" + source.getSlug(), page.getDraftDocument());
        return mapper.detail(page);
    }

    /** Soft delete. The slug stays reserved. A live page must be unpublished or archived first. */
    @Transactional
    public void delete(Long id) {
        CmsPage page = load(id);
        if (page.getStatus() == PageStatus.PUBLISHED) {
            throw new ConflictException("Unpublish or archive the page before deleting it");
        }
        assertNotReferenced(page, "deleted");
        page.setDeleted(true);
        page.setDeletedAt(Instant.now());
        page.setDeletedBy(SecurityUtils.getCurrentUsername().orElse("system"));
        pages.save(page);
    }

    /** Copies an earlier revision into the draft. Does not publish. */
    @Transactional
    public PageDetail rollback(Long id, int revisionNumber, Long expectedVersion) {
        CmsPage page = load(id);
        assertEditable(page);
        assertVersion(page, expectedVersion);

        CmsPageRevision revision = revisions.findByPageIdAndRevisionNumber(id, revisionNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Revision", "number", revisionNumber));
        if (revision.getKind() == RevisionKind.LEGACY) {
            throw new ConflictException("This revision was saved by the previous page editor and cannot be restored");
        }
        DocumentCheck check = validator.check(revision.getSnapshot(), Mode.DRAFT);
        if (check.hasErrors()) {
            throw new ConflictException("This revision no longer fits the current page format and cannot be restored");
        }
        applyDraft(page, check.document(), null);
        page = pages.saveAndFlush(page);

        recorder.record(page, RevisionKind.ROLLBACK, "Restored revision " + revisionNumber, page.getDraftDocument());
        return mapper.detail(page);
    }

    // ---------------------------------------------------------------------------------------------------------

    private CmsPage load(Long id) {
        return pages.findById(id).orElseThrow(() -> new ResourceNotFoundException("Page", "id", id));
    }

    private void assertEditable(CmsPage page) {
        if (page.getStatus() == PageStatus.ARCHIVED) {
            throw new ConflictException("The page is archived. Restore it before editing.");
        }
    }

    private void assertVersion(CmsPage page, Long expected) {
        if (expected == null || !expected.equals(page.getVersion())) {
            throw new ConflictException("This page was changed by someone else. Reload it to see the latest version.");
        }
    }

    private void assertNotReferenced(CmsPage page, String action) {
        List<String> places = referenceGuard.referencesTo(page.getId());
        if (!places.isEmpty()) {
            throw new ConflictException("The page cannot be " + action + " while it is in the navigation ("
                    + String.join(", ", places) + "). Remove it from the menu first.");
        }
    }

    private DocumentCheck checkForSave(JsonNode document) {
        DocumentCheck check = validator.check(document, Mode.DRAFT);
        if (check.hasErrors()) {
            throw invalid("The page content is not valid", check.errors());
        }
        return check;
    }

    private void applyDraft(CmsPage page, JsonNode normalised, String explicitTitle) {
        ObjectNode doc = normalised.deepCopy();
        String title = explicitTitle != null ? explicitTitle : doc.path("meta").path("en").path("title").asText("");
        if (explicitTitle != null && doc.path("meta").path("en").path("title").asText("").isBlank()) {
            ((ObjectNode) doc.with("meta").with("en")).put("title", explicitTitle);
        }
        page.setDraftDocument(doc);
        page.setTitle(truncate(title.isBlank() ? page.getSlug() : title, 150));
    }

    private void renameSlug(CmsPage page, String newSlug) {
        slugPolicy.assertAvailable(newSlug, page.getId());
        if (page.everPublished()) {
            // Changing a URL that may be linked from elsewhere is a publishing decision.
            if (!SecurityUtils.hasAuthority("PUBLISH_PAGES")) {
                throw new AccessDeniedException("Changing the address of a published page requires publish permission");
            }
            CmsPageSlugHistory old = new CmsPageSlugHistory();
            old.setPageId(page.getId());
            old.setSlug(page.getSlug());
            slugHistory.save(old);
        }
        // Going back to a former address of this same page frees that history entry.
        slugHistory.findBySlug(newSlug).filter(h -> h.getPageId().equals(page.getId())).ifPresent(slugHistory::delete);
        page.setSlug(newSlug);
    }

    private CmsPage saveUnique(CmsPage page, String slug) {
        try {
            return pages.saveAndFlush(page);
        } catch (DataIntegrityViolationException raced) {
            throw new DuplicateResourceException("A page with the address '" + slug + "' already exists (or used to)");
        }
    }

    private String freeCopySlug(String base) {
        for (int n = 1; n <= 50; n++) {
            String suffix = n == 1 ? "-copy" : "-copy-" + n;
            String candidate = truncate(base, SlugPolicy.MAX_LENGTH - suffix.length()).replaceAll("-+$", "") + suffix;
            if (slugPolicy.isAvailable(candidate, null)) {
                return candidate;
            }
        }
        throw new ConflictException("Could not find a free address for the copy; rename the original first");
    }

    private static ValidationFailedException invalid(String message, List<DocumentIssue> issues) {
        List<ValidationError> errors = issues.stream()
                .map(i -> new ValidationError(i.path(), i.message(), null))
                .toList();
        return new ValidationFailedException(message, errors);
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
