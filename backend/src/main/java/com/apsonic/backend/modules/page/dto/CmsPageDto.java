package com.apsonic.backend.modules.page.dto;

import com.apsonic.backend.modules.page.domain.enums.PageStatus;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CmsPageDto {
    private Long id;
    private String slug;
    
    private PageStatus status;
    
    private boolean navVisibility;
    private String navPosition;
    private Integer navOrder;
    
    private Map<String, CmsPageTranslationDto> translations;
    private List<CmsPageSectionDto> sections;
    
    private JsonNode publishedSnapshot;
    private Instant publishedAt;
    
    private Instant createdAt;
    private Instant updatedAt;
}
