package com.apsonic.backend.modules.page.service;

import com.apsonic.backend.modules.page.document.DocumentCheck;
import com.apsonic.backend.modules.page.document.DocumentValidator;
import com.apsonic.backend.modules.page.document.DocumentValidator.Mode;
import com.apsonic.backend.modules.page.domain.CmsPage;
import com.apsonic.backend.modules.page.domain.PageStatus;
import com.apsonic.backend.modules.page.dto.PageDtos.PageDetail;
import com.apsonic.backend.modules.page.dto.PageDtos.PageSummary;
import com.apsonic.backend.modules.page.dto.PageDtos.PreviewLink;
import com.apsonic.backend.modules.page.dto.PageDtos.PreviewPage;
import com.apsonic.backend.modules.page.dto.PageDtos.PublicPage;
import com.apsonic.backend.modules.page.dto.PageDtos.RevisionDetail;
import com.apsonic.backend.modules.page.dto.PageDtos.RevisionSummary;
import com.apsonic.backend.modules.page.dto.PageDtos.ValidationReport;
import com.apsonic.backend.modules.page.repository.CmsPageRepository;
import com.apsonic.backend.modules.page.repository.CmsPageRevisionRepository;
import com.apsonic.backend.modules.page.repository.CmsPageSlugHistoryRepository;
import com.apsonic.backend.shared.exception.ResourceNotFoundException;
import com.apsonic.backend.shared.html.HtmlSanitizer;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/** Read side: admin lists and details, revision history, validation reports, and what the public site may see. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PageQueryService {

    private final CmsPageRepository pages;
    private final CmsPageRevisionRepository revisions;
    private final CmsPageSlugHistoryRepository slugHistory;
    private final DocumentValidator validator;
    private final HtmlSanitizer htmlSanitizer;
    private final PreviewTokenService previewTokens;
    private final SlugPolicy slugPolicy;
    private final PageMapper mapper;

    /** Archived pages are listed only when asked for explicitly. */
    public Page<PageSummary> list(PageStatus status, String search, Pageable pageable) {
        Specification<CmsPage> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            } else {
                predicates.add(cb.notEqual(root.get("status"), PageStatus.ARCHIVED));
            }
            if (search != null && !search.isBlank()) {
                String like = "%" + search.strip().toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
                predicates.add(cb.or(cb.like(cb.lower(root.get("title")), like, '\\'),
                        cb.like(cb.lower(root.get("slug")), like, '\\')));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return pages.findAll(spec, pageable).map(mapper::summary);
    }

    public PageDetail get(Long id) {
        return mapper.detail(load(id));
    }

    public boolean slugAvailable(String slug, Long ownPageId) {
        return slugPolicy.isAvailable(slug, ownPageId);
    }

    public ValidationReport validate(Long id) {
        CmsPage page = load(id);
        DocumentCheck check = validator.check(page.getDraftDocument(), Mode.PUBLISH);
        return new ValidationReport(!check.hasErrors(), check.issues(), check.completeness());
    }

    public List<RevisionSummary> revisions(Long id) {
        load(id);
        return revisions.findByPageIdOrderByRevisionNumberDesc(id).stream().map(mapper::revisionSummary).toList();
    }

    public RevisionDetail revision(Long id, int number) {
        load(id);
        return revisions.findByPageIdAndRevisionNumber(id, number)
                .map(mapper::revisionDetail)
                .orElseThrow(() -> new ResourceNotFoundException("Revision", "number", number));
    }

    public PreviewLink previewLink(Long id) {
        load(id);
        PreviewTokenService.IssuedToken token = previewTokens.issue(id);
        return new PreviewLink(token.token(), token.expiresAt());
    }

    // --- public -----------------------------------------------------------------------------------------------

    /** The published copy of a page, or a redirect when the slug is a former address of a live page. */
    public PublicPage published(String slug) {
        var live = pages.findBySlugAndStatus(slug, PageStatus.PUBLISHED).filter(p -> p.getPublishedDocument() != null);
        if (live.isPresent()) {
            CmsPage page = live.get();
            return new PublicPage(page.getSlug(), null, page.getTitle(),
                    htmlSanitizer.sanitizeTree(page.getPublishedDocument()), page.getPublishedAt());
        }
        return slugHistory.findBySlug(slug)
                .flatMap(h -> pages.findById(h.getPageId()))
                .filter(p -> p.getStatus() == PageStatus.PUBLISHED)
                .map(p -> new PublicPage(p.getSlug(), p.getSlug(), p.getTitle(), null, p.getPublishedAt()))
                .orElseThrow(() -> new ResourceNotFoundException("Page", "slug", slug));
    }

    /**
     * The draft of a page for a holder of a valid preview token. Every failure (bad token, expired token, unknown
     * page) is the same 404 so the endpoint reveals nothing about which pages exist.
     */
    public PreviewPage preview(Long id, String token) {
        if (!previewTokens.isValidFor(token, id)) {
            throw new ResourceNotFoundException("Page", "id", id);
        }
        CmsPage page = pages.findById(id).orElseThrow(() -> new ResourceNotFoundException("Page", "id", id));
        return new PreviewPage(page.getId(), page.getSlug(), page.getTitle(), page.getStatus(),
                htmlSanitizer.sanitizeTree(page.getDraftDocument()));
    }

    private CmsPage load(Long id) {
        return pages.findById(id).orElseThrow(() -> new ResourceNotFoundException("Page", "id", id));
    }
}
