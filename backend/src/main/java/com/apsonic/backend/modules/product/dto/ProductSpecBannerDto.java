package com.apsonic.backend.modules.product.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductSpecBannerDto {
    private Long id;
    private String imageUrl;
    private String title;
    private String description;
    private String metricLabel;
    private String metricValue;
    private String metricUnit;
    private Integer displayOrder;
}
