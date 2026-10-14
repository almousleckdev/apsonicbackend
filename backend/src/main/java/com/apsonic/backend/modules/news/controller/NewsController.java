package com.apsonic.backend.modules.news.controller;

import com.apsonic.backend.modules.news.dto.NewsDetailResponse;
import com.apsonic.backend.modules.news.dto.NewsResponse;
import com.apsonic.backend.modules.news.service.NewsService;
import com.apsonic.backend.shared.api.ApiResponse;
import com.apsonic.backend.shared.api.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/news")
@RequiredArgsConstructor
public class NewsController {

    private final NewsService newsService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<NewsResponse>>> getPublicNewsList(
            @RequestParam(name = "locale", defaultValue = "en") String locale,
            @RequestParam(name = "category", required = false) String category,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        Page<NewsResponse> pageResult = newsService.getPublicNewsList(locale, category, pageable);
        return ResponseEntity.ok(ApiResponse.success("News list retrieved successfully", PageResponse.from(pageResult)));
    }

    @GetMapping("/{idOrSlug}")
    public ResponseEntity<ApiResponse<NewsDetailResponse>> getNewsDetail(
            @PathVariable(name = "idOrSlug") String idOrSlug,
            @RequestParam(name = "locale", defaultValue = "en") String locale
    ) {
        NewsDetailResponse response = newsService.getNewsByIdOrSlug(idOrSlug, locale);
        return ResponseEntity.ok(ApiResponse.success("News article retrieved successfully", response));
    }

    @PostMapping("/{id}/views")
    public ResponseEntity<ApiResponse<Void>> incrementViewsCount(@PathVariable(name = "id") Long id) {
        newsService.incrementViewsCount(id);
        return ResponseEntity.ok(ApiResponse.success("Views count incremented", null));
    }
}
