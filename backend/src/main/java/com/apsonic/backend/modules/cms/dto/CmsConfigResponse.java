package com.apsonic.backend.modules.cms.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CmsConfigResponse {
    private String configKey;
    private String configValue;
    private String description;
}
