package com.apsonic.backend.modules.page.controller;

import com.apsonic.backend.modules.page.dto.PageDtos.PreviewPage;
import com.apsonic.backend.modules.page.dto.PageDtos.PublicPage;
import com.apsonic.backend.modules.page.service.PageQueryService;
import com.apsonic.backend.shared.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Unauthenticated endpoints. The first serves published content only; the second serves a draft to a holder of a
 * signed, expiring, page-bound token and tells every cache and crawler to keep away.
 */
@RestController
@RequiredArgsConstructor
public class PublicPageController {

    private final PageQueryService queries;

    @GetMapping("/api/v1/pages/slug/{slug}")
    public ApiResponse<PublicPage> bySlug(@PathVariable String slug) {
        return ApiResponse.success(queries.published(slug));
    }

    @GetMapping("/api/v1/preview/pages/{id}")
    public ResponseEntity<ApiResponse<PreviewPage>> preview(@PathVariable Long id, @RequestParam String token) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore().mustRevalidate())
                .header("X-Robots-Tag", "noindex, nofollow, noarchive")
                .header("Referrer-Policy", "no-referrer")
                .body(ApiResponse.success(queries.preview(id, token)));
    }
}
