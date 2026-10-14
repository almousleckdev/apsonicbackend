package com.apsonic.backend.modules.page.service.impl;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import com.apsonic.backend.modules.page.dto.CmsPageRequest;
import com.apsonic.backend.modules.page.domain.enums.PageStatus;
import com.apsonic.backend.modules.page.service.CmsPageService;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
public class CmsPageServiceImplDebugger {

    @Autowired
    private CmsPageService cmsPageService;

    @Test
    public void testUpdatePage() {
        try {
            CmsPageRequest request = new CmsPageRequest();
            request.setSlug("test-slug-debug");
            request.setStatus(PageStatus.DRAFT);
            cmsPageService.updatePage(1L, request);
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
    }
}
