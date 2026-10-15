package com.apsonic.backend.modules.page.controller;

import com.apsonic.backend.modules.page.document.SectionRegistry;
import com.apsonic.backend.modules.page.domain.PageStatus;
import com.apsonic.backend.modules.page.dto.PageDtos.*;
import com.apsonic.backend.modules.page.service.PageCommandService;
import com.apsonic.backend.modules.page.service.PageQueryService;
import com.apsonic.backend.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Page management. Authorization is permission-based and enforced here, per action; the UI hiding a button is a
 * convenience, not a control.
 */
@RestController
@RequestMapping("/api/v1/admin/pages")
@RequiredArgsConstructor
@Tag(name = "Admin Pages", description = "CMS page builder")
public class AdminPageController {

    private static final int MAX_PAGE_SIZE = 100;

    private final PageQueryService queries;
    private final PageCommandService commands;
    private final SectionRegistry registry;

    @GetMapping
    @PreAuthorize("hasAuthority('READ_PAGES')")
    public ApiResponse<Page<PageSummary>> list(@RequestParam(required = false) PageStatus status,
                                               @RequestParam(required = false) String q,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "updatedAt"));
        return ApiResponse.success(queries.list(status, q, pageable));
    }

    @GetMapping("/section-types")
    @PreAuthorize("hasAuthority('READ_PAGES')")
    public ApiResponse<SectionTypesResponse> sectionTypes() {
        return ApiResponse.success(new SectionTypesResponse(registry.all(), registry.styleFields(),
                List.copyOf(SectionRegistry.LOCALES.stream().sorted().toList())));
    }

    @GetMapping("/slug-available")
    @PreAuthorize("hasAuthority('READ_PAGES')")
    public ApiResponse<SlugCheck> slugAvailable(@RequestParam String slug, @RequestParam(required = false) Long pageId) {
        return ApiResponse.success(new SlugCheck(queries.slugAvailable(slug, pageId)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('READ_PAGES')")
    public ApiResponse<PageDetail> get(@PathVariable Long id) {
        return ApiResponse.success(queries.get(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('WRITE_PAGES')")
    public ResponseEntity<ApiResponse<PageDetail>> create(@Valid @RequestBody CreatePageRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Page created", commands.create(request)));
    }

    /** Saves the draft. Never changes what is live. */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('WRITE_PAGES')")
    public ApiResponse<PageDetail> saveDraft(@PathVariable Long id, @Valid @RequestBody SaveDraftRequest request) {
        return ApiResponse.success("Draft saved", commands.saveDraft(id, request));
    }

    @PostMapping("/{id}/validate")
    @PreAuthorize("hasAuthority('READ_PAGES')")
    public ApiResponse<ValidationReport> validate(@PathVariable Long id) {
        return ApiResponse.success(queries.validate(id));
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("hasAuthority('PUBLISH_PAGES')")
    public ApiResponse<PageDetail> publish(@PathVariable Long id, @Valid @RequestBody VersionRequest request) {
        return ApiResponse.success("Page published", commands.publish(id, request.expectedVersion()));
    }

    @PostMapping("/{id}/unpublish")
    @PreAuthorize("hasAuthority('PUBLISH_PAGES')")
    public ApiResponse<PageDetail> unpublish(@PathVariable Long id, @Valid @RequestBody VersionRequest request) {
        return ApiResponse.success("Page unpublished", commands.unpublish(id, request.expectedVersion()));
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("hasAuthority('DELETE_PAGES')")
    public ApiResponse<PageDetail> archive(@PathVariable Long id, @Valid @RequestBody VersionRequest request) {
        return ApiResponse.success("Page archived", commands.archive(id, request.expectedVersion()));
    }

    @PostMapping("/{id}/restore")
    @PreAuthorize("hasAuthority('RESTORE_PAGES')")
    public ApiResponse<PageDetail> restore(@PathVariable Long id, @Valid @RequestBody VersionRequest request) {
        return ApiResponse.success("Page restored", commands.restore(id, request.expectedVersion()));
    }

    @PostMapping("/{id}/duplicate")
    @PreAuthorize("hasAuthority('WRITE_PAGES')")
    public ResponseEntity<ApiResponse<PageDetail>> duplicate(@PathVariable Long id) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Page duplicated", commands.duplicate(id)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('DELETE_PAGES')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        commands.delete(id);
        return ApiResponse.success("Page deleted", null);
    }

    @GetMapping("/{id}/revisions")
    @PreAuthorize("hasAuthority('READ_PAGES')")
    public ApiResponse<List<RevisionSummary>> revisions(@PathVariable Long id) {
        return ApiResponse.success(queries.revisions(id));
    }

    @GetMapping("/{id}/revisions/{number}")
    @PreAuthorize("hasAuthority('READ_PAGES')")
    public ApiResponse<RevisionDetail> revision(@PathVariable Long id, @PathVariable int number) {
        return ApiResponse.success(queries.revision(id, number));
    }

    @PostMapping("/{id}/revisions/{number}/rollback")
    @PreAuthorize("hasAuthority('RESTORE_PAGES')")
    public ApiResponse<PageDetail> rollback(@PathVariable Long id, @PathVariable int number,
                                            @Valid @RequestBody VersionRequest request) {
        return ApiResponse.success("Revision restored to the draft", commands.rollback(id, number, request.expectedVersion()));
    }

    @PostMapping("/{id}/preview-token")
    @PreAuthorize("hasAuthority('READ_PAGES')")
    public ApiResponse<PreviewLink> previewToken(@PathVariable Long id) {
        return ApiResponse.success(queries.previewLink(id));
    }
}
