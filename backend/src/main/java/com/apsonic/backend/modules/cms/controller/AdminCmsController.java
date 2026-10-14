package com.apsonic.backend.modules.cms.controller;

import com.apsonic.backend.modules.cms.dto.CmsConfigRequest;
import com.apsonic.backend.modules.cms.dto.CmsConfigResponse;
import com.apsonic.backend.modules.cms.service.SystemConfigService;
import com.apsonic.backend.shared.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/cms/config")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'EDITOR')")
public class AdminCmsController {

    private final SystemConfigService systemConfigService;

    @PutMapping("/{key}")
    public ResponseEntity<ApiResponse<CmsConfigResponse>> saveConfig(
            @PathVariable("key") String key,
            @Valid @RequestBody CmsConfigRequest request) {
        
        CmsConfigResponse response = systemConfigService.saveConfig(key, request);
        return ResponseEntity.ok(ApiResponse.success("Configuration saved successfully", response));
    }
}
