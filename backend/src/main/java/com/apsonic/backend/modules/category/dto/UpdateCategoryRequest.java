package com.apsonic.backend.modules.category.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateCategoryRequest {

    @Size(max = 100, message = "Slug must be at most 100 characters")
    private String slug;

    @Size(max = 200, message = "Name must be at most 200 characters")
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
}
