package com.apsonic.backend.modules.page.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NavigationItemDto {
    private String slug;
    private Map<String, String> labels;
    private String navPosition;
    private Integer navOrder;
}
