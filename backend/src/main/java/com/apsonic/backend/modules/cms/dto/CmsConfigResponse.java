package com.apsonic.backend.modules.cms.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CmsConfigResponse {
    private String configKey;
    private String configValue;
    private String description;
}
