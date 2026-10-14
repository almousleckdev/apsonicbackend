package com.apsonic.backend.modules.news.mapper;

import com.apsonic.backend.modules.news.domain.News;
import com.apsonic.backend.modules.news.domain.NewsSection;
import com.apsonic.backend.modules.news.dto.NewsDetailResponse;
import com.apsonic.backend.modules.news.dto.NewsResponse;
import com.apsonic.backend.modules.news.dto.NewsSectionDto;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class NewsMapper {

    public NewsResponse toNewsResponse(News news, String locale) {
        if (news == null) return null;

        String lang = (locale != null) ? locale.toLowerCase() : "en";
        String resolvedTitle = resolveTitle(news, lang);
        String resolvedSummary = resolveSummary(news, lang);

        return NewsResponse.builder()
                .id(news.getId())
                .slug(news.getSlug())
                .title(resolvedTitle)
                .titleEn(news.getTitleEn())
                .titleFr(news.getTitleFr())
                .titleZh(news.getTitleZh())
                .summary(resolvedSummary)
                .summaryEn(news.getSummaryEn())
                .summaryFr(news.getSummaryFr())
                .summaryZh(news.getSummaryZh())
                .coverImageUrl(news.getCoverImageUrl())
                .category(news.getCategory())
                .authorName(news.getAuthorName())
                .authorRole(news.getAuthorRole())
                .authorAvatarUrl(news.getAuthorAvatarUrl())
                .publishedAt(news.getPublishedAt())
                .published(news.getPublished())
                .viewsCount(news.getViewsCount())
                .readTimeMinutes(news.getReadTimeMinutes())
                .createdAt(news.getCreatedAt())
                .build();
    }

    public NewsDetailResponse toNewsDetailResponse(News news, String locale) {
        if (news == null) return null;

        String lang = (locale != null) ? locale.toLowerCase() : "en";
        String resolvedTitle = resolveTitle(news, lang);
        String resolvedSummary = resolveSummary(news, lang);
        String resolvedContentHtml = resolveContentHtml(news, lang);

        List<NewsSectionDto> sectionDtos = news.getSections() != null
                ? news.getSections().stream().map(this::toSectionDto)
                .collect(Collectors.toList())
                : Collections.emptyList();

        return NewsDetailResponse.builder()
                .id(news.getId())
                .slug(news.getSlug())
                .title(resolvedTitle)
                .titleEn(news.getTitleEn())
                .titleFr(news.getTitleFr())
                .titleZh(news.getTitleZh())
                .summary(resolvedSummary)
                .summaryEn(news.getSummaryEn())
                .summaryFr(news.getSummaryFr())
                .summaryZh(news.getSummaryZh())
                .contentHtml(resolvedContentHtml)
                .contentHtmlEn(news.getContentHtmlEn())
                .contentHtmlFr(news.getContentHtmlFr())
                .contentHtmlZh(news.getContentHtmlZh())
                .coverImageUrl(news.getCoverImageUrl())
                .category(news.getCategory())
                .authorName(news.getAuthorName())
                .authorRole(news.getAuthorRole())
                .authorAvatarUrl(news.getAuthorAvatarUrl())
                .publishedAt(news.getPublishedAt())
                .published(news.getPublished())
                .viewsCount(news.getViewsCount())
                .readTimeMinutes(news.getReadTimeMinutes())
                .createdAt(news.getCreatedAt())
                .sections(sectionDtos)
                .build();
    }

    public NewsSectionDto toSectionDto(NewsSection section) {
        if (section == null) return null;

        return NewsSectionDto.builder()
                .id(section.getId())
                .sectionType(section.getSectionType())
                .headingLevel(section.getHeadingLevel())
                .contentEn(section.getContentEn())
                .contentFr(section.getContentFr())
                .contentZh(section.getContentZh())
                .mediaUrl(section.getMediaUrl())
                .mediaCaptionEn(section.getMediaCaptionEn())
                .mediaCaptionFr(section.getMediaCaptionFr())
                .mediaCaptionZh(section.getMediaCaptionZh())
                .mediaAltText(section.getMediaAltText())
                .linkUrl(section.getLinkUrl())
                .linkTextEn(section.getLinkTextEn())
                .linkTextFr(section.getLinkTextFr())
                .linkTextZh(section.getLinkTextZh())
                .sectionOrder(section.getSectionOrder())
                .build();
    }

    public NewsSection toSectionEntity(NewsSectionDto dto) {
        if (dto == null) return null;

        return NewsSection.builder()
                .id(dto.getId())
                .sectionType(dto.getSectionType())
                .headingLevel(dto.getHeadingLevel())
                .contentEn(dto.getContentEn())
                .contentFr(dto.getContentFr())
                .contentZh(dto.getContentZh())
                .mediaUrl(dto.getMediaUrl())
                .mediaCaptionEn(dto.getMediaCaptionEn())
                .mediaCaptionFr(dto.getMediaCaptionFr())
                .mediaCaptionZh(dto.getMediaCaptionZh())
                .mediaAltText(dto.getMediaAltText())
                .linkUrl(dto.getLinkUrl())
                .linkTextEn(dto.getLinkTextEn())
                .linkTextFr(dto.getLinkTextFr())
                .linkTextZh(dto.getLinkTextZh())
                .sectionOrder(dto.getSectionOrder() != null ? dto.getSectionOrder() : 0)
                .build();
    }

    // Helper methods with robust language resolution & fallbacks
    private String resolveTitle(News news, String lang) {
        if ("fr".equals(lang) && news.getTitleFr() != null && !news.getTitleFr().isBlank()) {
            return news.getTitleFr();
        }
        if ("zh".equals(lang) && news.getTitleZh() != null && !news.getTitleZh().isBlank()) {
            return news.getTitleZh();
        }
        if ("en".equals(lang) && news.getTitleEn() != null && !news.getTitleEn().isBlank()) {
            return news.getTitleEn();
        }
        if (news.getTitleEn() != null && !news.getTitleEn().isBlank()) return news.getTitleEn();
        if (news.getTitleFr() != null && !news.getTitleFr().isBlank()) return news.getTitleFr();
        if (news.getTitleZh() != null && !news.getTitleZh().isBlank()) return news.getTitleZh();
        return "Untitled Article";
    }

    private String resolveSummary(News news, String lang) {
        if ("fr".equals(lang) && news.getSummaryFr() != null && !news.getSummaryFr().isBlank()) {
            return news.getSummaryFr();
        }
        if ("zh".equals(lang) && news.getSummaryZh() != null && !news.getSummaryZh().isBlank()) {
            return news.getSummaryZh();
        }
        if ("en".equals(lang) && news.getSummaryEn() != null && !news.getSummaryEn().isBlank()) {
            return news.getSummaryEn();
        }
        if (news.getSummaryEn() != null && !news.getSummaryEn().isBlank()) return news.getSummaryEn();
        if (news.getSummaryFr() != null && !news.getSummaryFr().isBlank()) return news.getSummaryFr();
        if (news.getSummaryZh() != null && !news.getSummaryZh().isBlank()) return news.getSummaryZh();
        return "";
    }

    private String resolveContentHtml(News news, String lang) {
        if ("fr".equals(lang) && news.getContentHtmlFr() != null && !news.getContentHtmlFr().isBlank()) {
            return news.getContentHtmlFr();
        }
        if ("zh".equals(lang) && news.getContentHtmlZh() != null && !news.getContentHtmlZh().isBlank()) {
            return news.getContentHtmlZh();
        }
        if ("en".equals(lang) && news.getContentHtmlEn() != null && !news.getContentHtmlEn().isBlank()) {
            return news.getContentHtmlEn();
        }
        if (news.getContentHtmlEn() != null && !news.getContentHtmlEn().isBlank()) return news.getContentHtmlEn();
        if (news.getContentHtmlFr() != null && !news.getContentHtmlFr().isBlank()) return news.getContentHtmlFr();
        if (news.getContentHtmlZh() != null && !news.getContentHtmlZh().isBlank()) return news.getContentHtmlZh();
        return "";
    }
}
