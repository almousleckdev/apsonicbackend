package com.apsonic.backend.modules.news.dto;

import com.apsonic.backend.modules.news.domain.SectionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NewsSectionDto {
    private Long id;
    private SectionType sectionType;
    private String headingLevel;
    private String contentEn;
    private String contentFr;
    private String contentZh;
    private String mediaUrl;
    private String mediaCaptionEn;
    private String mediaCaptionFr;
    private String mediaCaptionZh;
    private String mediaAltText;
    private String linkUrl;
    private String linkTextEn;
    private String linkTextFr;
    private String linkTextZh;
    private Integer sectionOrder;
}
