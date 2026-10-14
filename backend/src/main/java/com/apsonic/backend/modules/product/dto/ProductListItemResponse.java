package com.apsonic.backend.modules.product.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductListItemResponse {
    private Long id;
    private String slug;
    private String model;
    private String brand;
    private String category;
    private String categorySlug;
    private Integer displacement;
    private String image;
    private Boolean featured;
    private Boolean active;
    private Integer displayOrder;
    private Long viewsCount;
    private Instant createdAt;
}
