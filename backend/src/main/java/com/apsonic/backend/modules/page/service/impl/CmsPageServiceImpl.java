package com.apsonic.backend.modules.page.service.impl;

import com.apsonic.backend.modules.page.domain.CmsPage;
import com.apsonic.backend.modules.page.domain.CmsPageRevision;
import com.apsonic.backend.modules.page.domain.CmsPageSection;
import com.apsonic.backend.modules.page.domain.CmsPageTranslation;
import com.apsonic.backend.modules.page.domain.enums.PageStatus;
import com.apsonic.backend.modules.page.dto.*;
import com.apsonic.backend.modules.page.mapper.CmsPageMapper;
import com.apsonic.backend.modules.page.repository.CmsPageRepository;
import com.apsonic.backend.modules.page.repository.CmsPageRevisionRepository;
import com.apsonic.backend.modules.page.service.CmsPageService;
import com.apsonic.backend.shared.exception.DuplicateResourceException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CmsPageServiceImpl implements CmsPageService {

    private final CmsPageRepository cmsPageRepository;
    private final CmsPageRevisionRepository cmsPageRevisionRepository;
    private final CmsPageMapper cmsPageMapper;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public Page<CmsPageDto> getAllPages(Pageable pageable) {
        return cmsPageRepository.findAll(pageable).map(cmsPageMapper::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public CmsPageDto getPageById(Long id) {
        CmsPage page = cmsPageRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Page not found"));
        return cmsPageMapper.toDto(page);
    }

    @Override
    @Transactional(readOnly = true)
    public CmsPageDto getPublishedPageBySlug(String slug) {
        CmsPage page = cmsPageRepository.findBySlugAndStatus(slug, PageStatus.PUBLISHED)
                .orElseThrow(() -> new RuntimeException("Page not found"));
        
        // Ensure we only return the published snapshot to the public API
        CmsPageDto dto = new CmsPageDto();
        dto.setId(page.getId());
        dto.setSlug(page.getSlug());
        dto.setStatus(page.getStatus());
        dto.setPublishedSnapshot(page.getPublishedSnapshot());
        dto.setPublishedAt(page.getPublishedAt());
        return dto;
    }

    @Override
    @Transactional
    public CmsPageDto createPage(CmsPageRequest request) {
        if (cmsPageRepository.existsBySlug(request.getSlug())) {
            throw new RuntimeException("Slug already exists");
        }

        CmsPage page = CmsPage.builder()
                .slug(request.getSlug())
                .status(request.getStatus())
                .navVisibility(request.isNavVisibility())
                .navPosition(request.getNavPosition())
                .navOrder(request.getNavOrder() != null ? request.getNavOrder() : 0)
                .build();

        updateTranslations(page, request.getTranslations());
        
        if (request.getSections() != null) {
            updateSections(page, request.getSections());
        }

        CmsPage savedPage = cmsPageRepository.save(page);
        
        if (request.getStatus() == PageStatus.PUBLISHED) {
            return publishPage(savedPage.getId());
        }
        
        return cmsPageMapper.toDto(savedPage);
    }

    @Override
    @Transactional
    public CmsPageDto updatePage(Long id, CmsPageRequest request) {
        CmsPage page = cmsPageRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Page not found"));

        if (!page.getSlug().equals(request.getSlug()) && cmsPageRepository.existsBySlugAndIdNot(request.getSlug(), id)) {
            throw new DuplicateResourceException("CmsPage", "slug", request.getSlug());
        }

        page.setSlug(request.getSlug());
        page.setStatus(request.getStatus());
        page.setNavVisibility(request.isNavVisibility());
        page.setNavPosition(request.getNavPosition());
        page.setNavOrder(request.getNavOrder() != null ? request.getNavOrder() : 0);

        updateTranslations(page, request.getTranslations());

        if (request.getSections() != null) {
            updateSections(page, request.getSections());
        }

        CmsPage savedPage = cmsPageRepository.save(page);
        
        if (request.getStatus() == PageStatus.PUBLISHED) {
            return publishPage(savedPage.getId());
        }
        
        return cmsPageMapper.toDto(savedPage);
    }

    @Override
    @Transactional
    public void deletePage(Long id) {
        CmsPage page = cmsPageRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Page not found"));
        page.setDeleted(true);
        page.setDeletedAt(Instant.now());
        page.setDeletedBy(com.apsonic.backend.shared.util.SecurityUtils.getCurrentUsername().orElse("system"));
        cmsPageRepository.save(page);
    }

    @Override
    @Transactional
    public CmsPageDto updateStatus(Long id, PageStatus status) {
        if (status == PageStatus.PUBLISHED) {
            return publishPage(id);
        }
        CmsPage page = cmsPageRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Page not found"));
        page.setStatus(status);
        CmsPage savedPage = cmsPageRepository.save(page);
        return cmsPageMapper.toDto(savedPage);
    }
    
    @Transactional
    public CmsPageDto publishPage(Long id) {
        CmsPage page = cmsPageRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Page not found"));
                
        // Serialize the draft state to create the snapshot
        CmsPageDto draftDto = cmsPageMapper.toDto(page);
        JsonNode snapshot = objectMapper.valueToTree(draftDto);
        
        page.setStatus(PageStatus.PUBLISHED);
        page.setPublishedSnapshot(snapshot);
        page.setPublishedAt(Instant.now());
        
        CmsPage savedPage = cmsPageRepository.save(page);
        
        // Save Revision
        CmsPageRevision revision = CmsPageRevision.builder()
                .page(savedPage)
                .snapshot(snapshot)
                .createdBy(com.apsonic.backend.shared.util.SecurityUtils.getCurrentUsername().orElse("system"))
                .build();
        cmsPageRevisionRepository.save(revision);
        
        return cmsPageMapper.toDto(savedPage);
    }

    @Override
    @Transactional(readOnly = true)
    public List<NavigationItemDto> getNavigationTree() {
        List<CmsPage> pages = cmsPageRepository.findByNavVisibilityTrueAndStatusOrderByNavOrderAsc(PageStatus.PUBLISHED);
        return pages.stream().map(cmsPageMapper::toNavigationItemDto).collect(Collectors.toList());
    }

    private void updateTranslations(CmsPage page, Map<String, CmsPageTranslationDto> translationsMap) {
        if (translationsMap == null) {
            page.getTranslations().clear();
            return;
        }
        
        // Remove orphaned translations
        page.getTranslations().keySet().retainAll(translationsMap.keySet());
        
        // Update existing or add new
        translationsMap.forEach((locale, dto) -> {
            CmsPageTranslation existing = page.getTranslations().get(locale);
            if (existing != null) {
                existing.setTitle(dto.getTitle());
                existing.setNavLabel(dto.getNavLabel());
                existing.setSeoTitle(dto.getSeoTitle());
                existing.setSeoDescription(dto.getSeoDescription());
            } else {
                CmsPageTranslation translation = CmsPageTranslation.builder()
                        .locale(locale)
                        .title(dto.getTitle())
                        .navLabel(dto.getNavLabel())
                        .seoTitle(dto.getSeoTitle())
                        .seoDescription(dto.getSeoDescription())
                        .build();
                page.addTranslation(translation);
            }
        });
    }

    private void updateSections(CmsPage page, List<CmsPageSectionDto> sectionDtos) {
        page.getSections().clear();
        if (sectionDtos == null) return;
        
        for (int i = 0; i < sectionDtos.size(); i++) {
            CmsPageSectionDto dto = sectionDtos.get(i);
            CmsPageSection section = CmsPageSection.builder()
                    .sectionType(dto.getSectionType())
                    .sectionOrder(i) // Force exact array order
                    .configuration(dto.getConfiguration())
                    .build();
            page.addSection(section);
        }
    }
}
