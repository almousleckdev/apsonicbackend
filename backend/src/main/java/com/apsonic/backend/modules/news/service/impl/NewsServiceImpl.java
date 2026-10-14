package com.apsonic.backend.modules.news.service.impl;

import com.apsonic.backend.modules.news.domain.News;
import com.apsonic.backend.modules.news.domain.NewsSection;
import com.apsonic.backend.modules.news.dto.*;
import com.apsonic.backend.modules.news.mapper.NewsMapper;
import com.apsonic.backend.modules.news.repository.NewsRepository;
import com.apsonic.backend.modules.news.service.NewsService;
import com.apsonic.backend.modules.news.util.NewsSlugHelper;
import com.apsonic.backend.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class NewsServiceImpl implements NewsService {

    private final NewsRepository newsRepository;
    private final NewsMapper newsMapper;

    @Override
    @Transactional(readOnly = true)
    public Page<NewsResponse> getPublicNewsList(String locale, String category, Pageable pageable) {
        Page<News> page;
        if (category != null && !category.isBlank()) {
            page = newsRepository.findByPublishedTrueAndCategoryIgnoreCaseOrderByPublishedAtDesc(category, pageable);
        } else {
            page = newsRepository.findByPublishedTrueOrderByPublishedAtDesc(pageable);
        }
        return page.map(news -> newsMapper.toNewsResponse(news, locale));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<NewsResponse> getAllNewsForAdmin(String locale, String category, Pageable pageable) {
        Page<News> page;
        if (category != null && !category.isBlank()) {
            page = newsRepository.findByCategoryIgnoreCaseOrderByPublishedAtDesc(category, pageable);
        } else {
            page = newsRepository.findAll(pageable);
        }
        return page.map(news -> newsMapper.toNewsResponse(news, locale != null ? locale : "en"));
    }

    @Override
    @Transactional(readOnly = true)
    public NewsDetailResponse getNewsByIdOrSlug(String idOrSlug, String locale) {
        News news;
        if (idOrSlug.matches("\\d+")) {
            Long id = Long.parseLong(idOrSlug);
            news = newsRepository.findByIdWithSections(id)
                    .orElseThrow(() -> new ResourceNotFoundException("News article not found with id: " + id));
        } else {
            news = newsRepository.findBySlugWithSections(idOrSlug)
                    .orElseThrow(() -> new ResourceNotFoundException("News article not found with slug: " + idOrSlug));
        }
        return newsMapper.toNewsDetailResponse(news, locale);
    }

    @Override
    @Transactional
    public NewsDetailResponse createNews(CreateNewsRequest request) {
        String slug = generateUniqueSlug(request.getSlug(), request.getTitleEn());

        News news = News.builder()
                .titleEn(request.getTitleEn())
                .titleFr(request.getTitleFr())
                .titleZh(request.getTitleZh())
                .summaryEn(request.getSummaryEn())
                .summaryFr(request.getSummaryFr())
                .summaryZh(request.getSummaryZh())
                .contentHtmlEn(request.getContentHtmlEn())
                .contentHtmlFr(request.getContentHtmlFr())
                .contentHtmlZh(request.getContentHtmlZh())
                .slug(slug)
                .category(request.getCategory())
                .coverImageUrl(request.getCoverImageUrl())
                .authorName(request.getAuthorName() != null ? request.getAuthorName() : "APSONIC Team")
                .authorRole(request.getAuthorRole())
                .authorAvatarUrl(request.getAuthorAvatarUrl())
                .published(request.getPublished() != null ? request.getPublished() : false)
                .readTimeMinutes(request.getReadTimeMinutes() != null ? request.getReadTimeMinutes() : 5)
                .viewsCount(0L)
                .build();

        if (request.getSections() != null) {
            for (NewsSectionDto secDto : request.getSections()) {
                NewsSection section = newsMapper.toSectionEntity(secDto);
                if (section != null) {
                    news.addSection(section);
                }
            }
        }

        News saved = newsRepository.save(news);
        log.info("Created news article with ID: {}, slug: {}", saved.getId(), saved.getSlug());
        return newsMapper.toNewsDetailResponse(saved, "en");
    }

    @Override
    @Transactional
    public NewsDetailResponse updateNews(String idOrSlug, UpdateNewsRequest request) {
        News news = findNewsEntity(idOrSlug, true);

        if (request.getTitleEn() != null) news.setTitleEn(request.getTitleEn());
        if (request.getTitleFr() != null) news.setTitleFr(request.getTitleFr());
        if (request.getTitleZh() != null) news.setTitleZh(request.getTitleZh());
        if (request.getSummaryEn() != null) news.setSummaryEn(request.getSummaryEn());
        if (request.getSummaryFr() != null) news.setSummaryFr(request.getSummaryFr());
        if (request.getSummaryZh() != null) news.setSummaryZh(request.getSummaryZh());
        if (request.getContentHtmlEn() != null) news.setContentHtmlEn(request.getContentHtmlEn());
        if (request.getContentHtmlFr() != null) news.setContentHtmlFr(request.getContentHtmlFr());
        if (request.getContentHtmlZh() != null) news.setContentHtmlZh(request.getContentHtmlZh());

        if (request.getSlug() != null && !request.getSlug().isBlank() && !request.getSlug().equals(news.getSlug())) {
            news.setSlug(generateUniqueSlug(request.getSlug(), request.getTitleEn()));
        }

        if (request.getCategory() != null) news.setCategory(request.getCategory());
        if (request.getCoverImageUrl() != null) news.setCoverImageUrl(request.getCoverImageUrl());
        if (request.getAuthorName() != null) news.setAuthorName(request.getAuthorName());
        if (request.getAuthorRole() != null) news.setAuthorRole(request.getAuthorRole());
        if (request.getAuthorAvatarUrl() != null) news.setAuthorAvatarUrl(request.getAuthorAvatarUrl());
        if (request.getPublished() != null) news.setPublished(request.getPublished());
        if (request.getReadTimeMinutes() != null) news.setReadTimeMinutes(request.getReadTimeMinutes());

        if (request.getSections() != null) {
            news.clearSections();
            for (NewsSectionDto secDto : request.getSections()) {
                NewsSection section = newsMapper.toSectionEntity(secDto);
                if (section != null) {
                    news.addSection(section);
                }
            }
        }

        News updated = newsRepository.save(news);
        log.info("Updated news article ID: {}", updated.getId());
        return newsMapper.toNewsDetailResponse(updated, "en");
    }

    @Override
    @Transactional
    public void deleteNews(String idOrSlug) {
        News news = findNewsEntity(idOrSlug, false);
        news.setDeleted(true);
        news.setDeletedAt(java.time.Instant.now());
        news.setDeletedBy(com.apsonic.backend.shared.util.SecurityUtils.getCurrentUsername().orElse("system"));
        newsRepository.save(news);
        log.info("Soft deleted news article ID: {}", news.getId());
    }

    @Override
    @Transactional
    public NewsDetailResponse togglePublishStatus(String idOrSlug, Boolean published) {
        News news = findNewsEntity(idOrSlug, false);
        boolean targetStatus = published != null ? published : !Boolean.TRUE.equals(news.getPublished());
        news.setPublished(targetStatus);
        News saved = newsRepository.save(news);
        return newsMapper.toNewsDetailResponse(saved, "en");
    }

    private News findNewsEntity(String idOrSlug, boolean withSections) {
        if (idOrSlug == null || idOrSlug.trim().isEmpty()) {
            throw new ResourceNotFoundException("News", "identifier", "empty");
        }
        String clean = idOrSlug.trim();
        if (clean.matches("\\d+")) {
            Long id = Long.parseLong(clean);
            return withSections
                    ? newsRepository.findByIdWithSections(id).orElseThrow(() -> new ResourceNotFoundException("News", "id", clean))
                    : newsRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("News", "id", clean));
        } else {
            return withSections
                    ? newsRepository.findBySlugWithSections(clean).orElseThrow(() -> new ResourceNotFoundException("News", "slug", clean))
                    : newsRepository.findBySlug(clean).orElseThrow(() -> new ResourceNotFoundException("News", "slug", clean));
        }
    }

    @Override
    @Transactional
    public void incrementViewsCount(Long id) {
        newsRepository.incrementViewsCountNative(id);
    }

    private String generateUniqueSlug(String customSlug, String title) {
        String baseSlug = NewsSlugHelper.generateSlug((customSlug != null && !customSlug.isBlank()) ? customSlug : title);
        String candidate = baseSlug;
        int attempts = 0;
        while (newsRepository.existsBySlug(candidate) && attempts < 10) {
            candidate = NewsSlugHelper.appendUniqueSuffix(baseSlug);
            attempts++;
        }
        return candidate;
    }
}
