package com.apsonic.backend.modules.news.service;

import com.apsonic.backend.modules.news.dto.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface NewsService {
    Page<NewsResponse> getPublicNewsList(String locale, String category, Pageable pageable);
    Page<NewsResponse> getAllNewsForAdmin(String locale, String category, Pageable pageable);
    NewsDetailResponse getNewsByIdOrSlug(String idOrSlug, String locale);
    NewsDetailResponse createNews(CreateNewsRequest request);
    NewsDetailResponse updateNews(String idOrSlug, UpdateNewsRequest request);
    void deleteNews(String idOrSlug);
    NewsDetailResponse togglePublishStatus(String idOrSlug, Boolean published);
    void incrementViewsCount(Long id);
}
