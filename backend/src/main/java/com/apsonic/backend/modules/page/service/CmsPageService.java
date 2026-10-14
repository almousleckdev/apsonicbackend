package com.apsonic.backend.modules.page.service;

import com.apsonic.backend.modules.page.dto.CmsPageDto;
import com.apsonic.backend.modules.page.dto.CmsPageRequest;
import com.apsonic.backend.modules.page.dto.NavigationItemDto;
import com.apsonic.backend.modules.page.domain.enums.PageStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface CmsPageService {
    Page<CmsPageDto> getAllPages(Pageable pageable);
    CmsPageDto getPageById(Long id);
    CmsPageDto getPublishedPageBySlug(String slug);
    CmsPageDto createPage(CmsPageRequest request);
    CmsPageDto updatePage(Long id, CmsPageRequest request);
    void deletePage(Long id);
    CmsPageDto updateStatus(Long id, PageStatus status);
    List<NavigationItemDto> getNavigationTree();
}
