package com.apsonic.backend.modules.page.service;

import com.apsonic.backend.modules.page.domain.CmsPage;
import com.apsonic.backend.modules.page.domain.CmsPageRevision;
import com.apsonic.backend.modules.page.dto.PageDtos.PageDetail;
import com.apsonic.backend.modules.page.dto.PageDtos.PageSummary;
import com.apsonic.backend.modules.page.dto.PageDtos.RevisionDetail;
import com.apsonic.backend.modules.page.dto.PageDtos.RevisionSummary;
import org.springframework.stereotype.Component;

@Component
class PageMapper {

    PageSummary summary(CmsPage p) {
        return new PageSummary(p.getId(), p.getSlug(), p.getTitle(), p.getStatus(), p.hasUnpublishedChanges(),
                p.getVersion(), p.getCreatedAt(), p.getCreatedBy(), p.getUpdatedAt(), p.getUpdatedBy(), p.getPublishedAt());
    }

    PageDetail detail(CmsPage p) {
        return new PageDetail(p.getId(), p.getSlug(), p.getTitle(), p.getStatus(), p.hasUnpublishedChanges(),
                p.getVersion(), p.getCreatedAt(), p.getCreatedBy(), p.getUpdatedAt(), p.getUpdatedBy(),
                p.getPublishedAt(), p.getPublishedBy(), p.getArchivedAt(), p.getDraftDocument());
    }

    RevisionSummary revisionSummary(CmsPageRevision r) {
        return new RevisionSummary(r.getRevisionNumber(), r.getKind(), r.getNote(), r.getCreatedBy(), r.getSavedAt());
    }

    RevisionDetail revisionDetail(CmsPageRevision r) {
        return new RevisionDetail(r.getRevisionNumber(), r.getKind(), r.getNote(), r.getCreatedBy(), r.getSavedAt(),
                r.getSnapshot());
    }
}
