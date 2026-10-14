package com.apsonic.backend.modules.product.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductSpecDto {
    private Long id;
    private String specKey;
    private String specValue;
    private String specGroup;
    private String unit;
    private Boolean isKeyMetric;
    private Integer displayOrder;
}
