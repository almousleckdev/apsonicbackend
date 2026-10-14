package com.apsonic.backend.modules.product.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductColorDto {
    private Long id;
    private String colorName;
    private String hexCode;
    private String imageUrl;
    private Integer displayOrder;
}
