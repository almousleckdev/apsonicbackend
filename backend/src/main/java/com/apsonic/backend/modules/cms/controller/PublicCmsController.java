package com.apsonic.backend.modules.cms.controller;

import com.apsonic.backend.modules.cms.dto.CmsConfigResponse;
import com.apsonic.backend.modules.cms.service.SystemConfigService;
import com.apsonic.backend.shared.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/cms/config")
@RequiredArgsConstructor
public class PublicCmsController {

    private final SystemConfigService systemConfigService;

    @GetMapping("/{key}")
    public ResponseEntity<ApiResponse<CmsConfigResponse>> getConfig(@PathVariable("key") String key) {
        CmsConfigResponse response = systemConfigService.getConfig(key);
        return ResponseEntity.ok(ApiResponse.success("Configuration retrieved", response));
    }
}
