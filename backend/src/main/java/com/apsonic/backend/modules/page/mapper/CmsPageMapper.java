package com.apsonic.backend.modules.page.mapper;

import com.apsonic.backend.modules.page.domain.CmsPage;
import com.apsonic.backend.modules.page.domain.CmsPageSection;
import com.apsonic.backend.modules.page.domain.CmsPageTranslation;
import com.apsonic.backend.modules.page.dto.CmsPageDto;
import com.apsonic.backend.modules.page.dto.CmsPageSectionDto;
import com.apsonic.backend.modules.page.dto.CmsPageTranslationDto;
import com.apsonic.backend.modules.page.dto.NavigationItemDto;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class CmsPageMapper {

    public CmsPageDto toDto(CmsPage page) {
        if (page == null) {
            return null;
        }

        Map<String, CmsPageTranslationDto> translationDtos = new HashMap<>();
        if (page.getTranslations() != null) {
            page.getTranslations().forEach((locale, trans) -> {
                translationDtos.put(locale, CmsPageTranslationDto.builder()
                        .title(trans.getTitle())
                        .navLabel(trans.getNavLabel())
                        .seoTitle(trans.getSeoTitle())
                        .seoDescription(trans.getSeoDescription())
                        .build());
            });
        }
        
        return CmsPageDto.builder()
                .id(page.getId())
                .slug(page.getSlug())
                .status(page.getStatus())
                .navVisibility(page.isNavVisibility())
                .navPosition(page.getNavPosition())
                .navOrder(page.getNavOrder())
                .translations(translationDtos)
                .publishedSnapshot(page.getPublishedSnapshot())
                .publishedAt(page.getPublishedAt())
                .createdAt(page.getCreatedAt())
                .updatedAt(page.getUpdatedAt())
                .sections(page.getSections() != null ? 
                        page.getSections().stream().map(this::toSectionDto).collect(Collectors.toList()) 
                        : null)
                .build();
    }

    public CmsPageSectionDto toSectionDto(CmsPageSection section) {
        if (section == null) {
            return null;
        }
        return CmsPageSectionDto.builder()
                .id(section.getId())
                .sectionType(section.getSectionType())
                .sectionOrder(section.getSectionOrder())
                .configuration(section.getConfiguration())
                .build();
    }

    public NavigationItemDto toNavigationItemDto(CmsPage page) {
        if (page == null) {
            return null;
        }
        
        Map<String, String> labels = new HashMap<>();
        if (page.getTranslations() != null) {
            page.getTranslations().forEach((locale, trans) -> {
                if (trans.getNavLabel() != null) {
                    labels.put(locale, trans.getNavLabel());
                } else {
                    labels.put(locale, trans.getTitle());
                }
            });
        }
        
        return NavigationItemDto.builder()
                .slug(page.getSlug())
                .labels(labels)
                .navPosition(page.getNavPosition())
                .navOrder(page.getNavOrder())
                .build();
    }
}
