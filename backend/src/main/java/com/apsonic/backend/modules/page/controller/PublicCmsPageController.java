package com.apsonic.backend.modules.page.controller;

import com.apsonic.backend.modules.page.dto.CmsPageDto;
import com.apsonic.backend.modules.page.dto.NavigationItemDto;
import com.apsonic.backend.modules.page.service.CmsPageService;
import com.apsonic.backend.shared.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/pages")
@RequiredArgsConstructor
public class PublicCmsPageController {

    private final CmsPageService cmsPageService;

    @GetMapping("/slug/{slug}")
    public ResponseEntity<ApiResponse<CmsPageDto>> getPageBySlug(@PathVariable String slug) {
        return ResponseEntity.ok(ApiResponse.success(cmsPageService.getPublishedPageBySlug(slug)));
    }

    @GetMapping("/navigation")
    public ResponseEntity<ApiResponse<List<NavigationItemDto>>> getNavigationTree() {
        return ResponseEntity.ok(ApiResponse.success(cmsPageService.getNavigationTree()));
    }
}
