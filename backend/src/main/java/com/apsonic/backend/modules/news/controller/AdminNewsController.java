package com.apsonic.backend.modules.news.controller;

import com.apsonic.backend.modules.news.dto.*;
import com.apsonic.backend.modules.news.service.NewsService;
import com.apsonic.backend.shared.api.ApiResponse;
import com.apsonic.backend.shared.api.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/news")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'EDITOR')")
public class AdminNewsController {

    private final NewsService newsService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<NewsResponse>>> getAllNewsForAdmin(
            @RequestParam(name = "locale", defaultValue = "en") String locale,
            @RequestParam(name = "category", required = false) String category,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        Page<NewsResponse> pageResult = newsService.getAllNewsForAdmin(locale, category, pageable);
        return ResponseEntity.ok(ApiResponse.success("Admin news list retrieved successfully", PageResponse.from(pageResult)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<NewsDetailResponse>> createNews(@Valid @RequestBody CreateNewsRequest request) {
        NewsDetailResponse response = newsService.createNews(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("News article created successfully", response));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<NewsDetailResponse>> updateNews(
            @PathVariable(name = "id") String id,
            @RequestBody UpdateNewsRequest request
    ) {
        NewsDetailResponse response = newsService.updateNews(id, request);
        return ResponseEntity.ok(ApiResponse.success("News article updated successfully", response));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteNews(@PathVariable(name = "id") String id) {
        newsService.deleteNews(id);
        return ResponseEntity.ok(ApiResponse.success("News article deleted successfully", null));
    }

    @PatchMapping("/{id}/publish")
    public ResponseEntity<ApiResponse<NewsDetailResponse>> togglePublishStatus(
            @PathVariable(name = "id") String id,
            @RequestParam(name = "published") Boolean published
    ) {
        NewsDetailResponse response = newsService.togglePublishStatus(id, published);
        return ResponseEntity.ok(ApiResponse.success("News article publish status updated", response));
    }
}
