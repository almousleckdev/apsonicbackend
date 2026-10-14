package com.apsonic.backend.modules.page.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.beans.factory.annotation.Autowired;
import com.apsonic.backend.modules.page.service.CmsPageService;
import com.apsonic.backend.modules.page.dto.CmsPageRequest;
import com.apsonic.backend.modules.page.dto.CmsPageTranslationDto;
import com.apsonic.backend.modules.page.domain.enums.PageStatus;
import org.springframework.http.ResponseEntity;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

@RestController
public class DebugController {
    @Autowired
    private CmsPageService cmsPageService;

    @GetMapping("/api/v1/pages/debug-create2")
    public ResponseEntity<String> debug() {
        try {
            CmsPageRequest request = new CmsPageRequest();
            request.setSlug("debug-create-slug2-" + System.currentTimeMillis());
            request.setStatus(PageStatus.DRAFT);
            Map<String, CmsPageTranslationDto> map = new HashMap<>();
            map.put("en", CmsPageTranslationDto.builder().title("Title").build());
            request.setTranslations(map);
            cmsPageService.createPage(request);
            return ResponseEntity.ok("Success2");
        } catch (Exception e) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            return ResponseEntity.internalServerError().body(sw.toString());
        }
    }
}
