package com.apsonic.backend.modules.news.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NewsDetailResponse {
    private Long id;
    private String slug;
    private String title;
    private String titleEn;
    private String titleFr;
    private String titleZh;
    private String summary;
    private String summaryEn;
    private String summaryFr;
    private String summaryZh;
    private String contentHtml;
    private String contentHtmlEn;
    private String contentHtmlFr;
    private String contentHtmlZh;
    private String coverImageUrl;
    private String category;
    private String authorName;
    private String authorRole;
    private String authorAvatarUrl;
    private OffsetDateTime publishedAt;
    private Boolean published;
    private Long viewsCount;
    private Integer readTimeMinutes;
    private java.time.Instant createdAt;

    @Builder.Default
    private List<NewsSectionDto> sections = new ArrayList<>();
}
