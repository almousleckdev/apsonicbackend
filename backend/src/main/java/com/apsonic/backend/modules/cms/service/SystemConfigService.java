package com.apsonic.backend.modules.cms.service;

import com.apsonic.backend.modules.cms.dto.CmsConfigRequest;
import com.apsonic.backend.modules.cms.dto.CmsConfigResponse;

public interface SystemConfigService {
    CmsConfigResponse getConfig(String configKey);
    CmsConfigResponse saveConfig(String configKey, CmsConfigRequest request);
}
