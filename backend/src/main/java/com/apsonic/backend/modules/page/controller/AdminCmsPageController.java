package com.apsonic.backend.modules.page.controller;

import com.apsonic.backend.modules.page.domain.enums.PageStatus;
import com.apsonic.backend.modules.page.dto.CmsPageDto;
import com.apsonic.backend.modules.page.dto.CmsPageRequest;
import com.apsonic.backend.modules.page.service.CmsPageService;
import com.apsonic.backend.shared.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/pages")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
public class AdminCmsPageController {

    private final CmsPageService cmsPageService;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<CmsPageDto>>> getAllPages(Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(cmsPageService.getAllPages(pageable)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CmsPageDto>> getPageById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(cmsPageService.getPageById(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CmsPageDto>> createPage(@Valid @RequestBody CmsPageRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Page created successfully", cmsPageService.createPage(request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CmsPageDto>> updatePage(
            @PathVariable Long id,
            @Valid @RequestBody CmsPageRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Page updated successfully", cmsPageService.updatePage(id, request)));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<CmsPageDto>> updateStatus(
            @PathVariable Long id,
            @RequestParam PageStatus status) {
        return ResponseEntity.ok(ApiResponse.success("Page status updated successfully", cmsPageService.updateStatus(id, status)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deletePage(@PathVariable Long id) {
        cmsPageService.deletePage(id);
        return ResponseEntity.ok(ApiResponse.success("Page deleted successfully", null));
    }
}
