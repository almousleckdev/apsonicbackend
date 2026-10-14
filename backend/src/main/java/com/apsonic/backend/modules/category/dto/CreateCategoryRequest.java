package com.apsonic.backend.modules.category.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateCategoryRequest {

    @NotBlank(message = "Category slug is required")
    @Size(max = 100, message = "Slug must be at most 100 characters")
    private String slug;

    @NotBlank(message = "English name is required")
    @Size(max = 200, message = "Name must be at most 200 characters")
    private String nameEn;

    private String nameFr;
    private String nameZh;
    private String imageUrl;

    @Builder.Default
    private String brand = "apsonic";

    @Builder.Default
    private String parentType = "two-wheeler";

    @Builder.Default
    private Double imageScale = 1.0;

    @Builder.Default
    private Integer imageTranslateY = 0;

    @Builder.Default
    private Integer displayOrder = 0;

    @Builder.Default
    private Boolean active = true;
}
