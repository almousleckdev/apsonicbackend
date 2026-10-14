package com.apsonic.backend.modules.cms.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CmsConfigRequest {
    @NotBlank(message = "Config value is required")
    private String configValue;
    private String description;
}
