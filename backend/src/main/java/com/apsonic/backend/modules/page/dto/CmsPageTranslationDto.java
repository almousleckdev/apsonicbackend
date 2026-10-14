package com.apsonic.backend.modules.page.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CmsPageTranslationDto {
    private String title;
    private String navLabel;
    private String seoTitle;
    private String seoDescription;
}
