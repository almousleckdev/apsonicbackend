package com.apsonic.backend.modules.news.dto;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateNewsRequest {

    private String slug;
    private String titleEn;
    private String titleFr;
    private String titleZh;

    private String summaryEn;
    private String summaryFr;
    private String summaryZh;

    private String contentHtmlEn;
    private String contentHtmlFr;
    private String contentHtmlZh;

    private String coverImageUrl;
    private String category;
    private String authorName;
    private String authorRole;
    private String authorAvatarUrl;
    private Boolean published;
    private Integer readTimeMinutes;
    @Valid
    private List<NewsSectionDto> sections;
}
