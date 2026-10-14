package com.apsonic.backend.modules.product.dto;

import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProductRequest {

    private String slug;

    @Size(max = 200, message = "Model name must be at most 200 characters")
    private String modelName;

    private Long categoryId;
    private String brand;
    private Integer displacement;
    private String coverImageUrl;
    private String heroImageUrl;
    private String performanceImageUrl;
    private String storyTitle;
    private String storyDescription;
    private String storyBackgroundImage;
    private String aestheticTitle;
    private String aestheticSubtitle;
    private String aestheticBgImage;
    private String aestheticImage1;
    private String aestheticImage2;
    private Boolean featured;
    private Boolean active;
    private Integer displayOrder;

    @Valid
    private List<ProductColorDto> colors;
    @Valid
    private List<ProductSpecBannerDto> specBanners;
    @Valid
    private List<ProductSpecDto> specs;
    @Valid
    private List<ProductReviewDto> reviews;
}
