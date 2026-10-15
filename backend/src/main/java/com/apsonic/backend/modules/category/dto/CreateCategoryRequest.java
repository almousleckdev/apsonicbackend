package com.apsonic.backend.modules.category.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
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
    @Pattern(regexp = CategoryRules.SLUG, message = "Slug may only contain letters, digits, dashes and underscores")
    private String slug;

    @NotBlank(message = "English name is required")
    @Size(max = 200, message = "Name must be at most 200 characters")
    @Pattern(regexp = CategoryRules.NOT_BLANK, message = "English name must not be blank")
    private String nameEn;

    @Size(max = 200, message = "Name must be at most 200 characters")
    private String nameFr;
    @Size(max = 200, message = "Name must be at most 200 characters")
    private String nameZh;
    @Size(max = 2048, message = "Image address is too long")
    @Pattern(regexp = CategoryRules.IMAGE, message = "Image must be a site path (/uploads/...) or an http(s) address")
    private String imageUrl;

    @Builder.Default
    @Size(max = 100, message = "Brand must be at most 100 characters")
    @Pattern(regexp = CategoryRules.KEY, message = "Brand must be lowercase words joined by dashes")
    private String brand = "apsonic";

    @Builder.Default
    @Size(max = 50, message = "Group must be at most 50 characters")
    @Pattern(regexp = CategoryRules.KEY, message = "Group must be lowercase words joined by dashes")
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
