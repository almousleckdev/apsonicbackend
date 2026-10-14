package com.apsonic.backend.modules.page.dto;

import com.apsonic.backend.modules.page.domain.enums.PageStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CmsPageRequest {
    
    @NotBlank(message = "Slug is required")
    @Pattern(regexp = "^[a-z0-9-]+$", message = "Slug must contain only lowercase letters, numbers, and hyphens")
    private String slug;
    
    @NotNull(message = "Translations are required")
    private Map<String, CmsPageTranslationDto> translations;
    
    @NotNull(message = "Status is required")
    private PageStatus status;
    
    private boolean navVisibility;
    private String navPosition;
    private Integer navOrder;
    
    private List<CmsPageSectionDto> sections;
}
