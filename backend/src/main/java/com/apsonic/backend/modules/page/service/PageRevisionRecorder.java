package com.apsonic.backend.modules.page.service;

import com.apsonic.backend.modules.page.domain.CmsPage;
import com.apsonic.backend.modules.page.domain.CmsPageRevision;
import com.apsonic.backend.modules.page.domain.RevisionKind;
import com.apsonic.backend.modules.page.repository.CmsPageRevisionRepository;
import com.apsonic.backend.shared.util.SecurityUtils;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Writes revision history. Draft saves by the same editor within a few minutes update one revision instead of adding
 * a new one each time, so autosave cannot flood the history; every publish, rollback and creation always gets its own.
 */
@Component
@RequiredArgsConstructor
class PageRevisionRecorder {

    static final Duration MERGE_WINDOW = Duration.ofMinutes(5);
    static final int KEEP_DRAFT_REVISIONS = 100;

    private final CmsPageRevisionRepository revisions;

    void record(CmsPage page, RevisionKind kind, String note, JsonNode document) {
        String user = SecurityUtils.getCurrentUsername().orElse("system");
        Optional<CmsPageRevision> latest = revisions.findTopByPageIdOrderByRevisionNumberDesc(page.getId());

        if (kind == RevisionKind.DRAFT && latest.isPresent()) {
            CmsPageRevision last = latest.get();
            boolean mergeable = last.getKind() == RevisionKind.DRAFT
                    && user.equals(last.getCreatedBy())
                    && Duration.between(last.getSavedAt(), Instant.now()).compareTo(MERGE_WINDOW) < 0;
            if (mergeable) {
                last.setSnapshot(document);
                last.setSavedAt(Instant.now());
                revisions.save(last);
                return;
            }
        }

        CmsPageRevision revision = new CmsPageRevision();
        revision.setPage(page);
        revision.setRevisionNumber(latest.map(r -> r.getRevisionNumber() + 1).orElse(1));
        revision.setKind(kind);
        revision.setNote(note);
        revision.setSnapshot(document);
        revision.setCreatedBy(user);
        revisions.save(revision);

        if (kind == RevisionKind.DRAFT) {
            revisions.pruneDraftRevisions(page.getId(), KEEP_DRAFT_REVISIONS);
        }
    }
}
