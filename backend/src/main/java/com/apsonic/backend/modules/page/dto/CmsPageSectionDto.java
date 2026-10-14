package com.apsonic.backend.modules.page.dto;

import com.apsonic.backend.modules.page.domain.enums.SectionType;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CmsPageSectionDto {
    private Long id;
    private SectionType sectionType;
    private Integer sectionOrder;
    private JsonNode configuration;
}
