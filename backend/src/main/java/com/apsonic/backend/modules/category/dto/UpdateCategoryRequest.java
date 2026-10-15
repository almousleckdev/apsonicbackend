package com.apsonic.backend.modules.category.dto;

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
public class UpdateCategoryRequest {

    @Size(max = 100, message = "Slug must be at most 100 characters")
    @Pattern(regexp = CategoryRules.SLUG, message = "Slug may only contain letters, digits, dashes and underscores")
    private String slug;

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
    @Size(max = 100, message = "Brand must be at most 100 characters")
    @Pattern(regexp = CategoryRules.KEY, message = "Brand must be lowercase words joined by dashes")
    private String brand;
    @Size(max = 50, message = "Group must be at most 50 characters")
    @Pattern(regexp = CategoryRules.KEY, message = "Group must be lowercase words joined by dashes")
    private String parentType;
    private Double imageScale;
    private Integer imageTranslateY;
    private Integer displayOrder;
    private Boolean active;
}
