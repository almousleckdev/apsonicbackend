package com.apsonic.backend.modules.category.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryResponse {
    private Long id;
    private String slug;
    private String name;
    private String nameEn;
    private String nameFr;
    private String nameZh;
    private String imageUrl;
    private String brand;
    private String parentType;
    private Double imageScale;
    private Integer imageTranslateY;
    private Integer displayOrder;
    private Boolean active;
    private Instant createdAt;
}
