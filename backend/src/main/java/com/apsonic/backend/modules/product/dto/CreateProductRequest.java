package com.apsonic.backend.modules.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateProductRequest {

    private String slug;

    @NotBlank(message = "Model name is required")
    @Size(max = 200, message = "Model name must be at most 200 characters")
    private String modelName;

    @NotNull(message = "Category ID is required")
    private Long categoryId;

    @Builder.Default
    private String brand = "apsonic";

    private Integer displacement;

    @NotBlank(message = "Cover image URL is required")
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

    @Builder.Default
    private Boolean featured = false;

    @Builder.Default
    private Boolean active = true;

    @Builder.Default
    private Integer displayOrder = 0;

    @Valid
    @NotNull
    @Builder.Default
    private List<ProductColorDto> colors = new ArrayList<>();

    @Valid
    @NotNull
    @Builder.Default
    private List<ProductSpecBannerDto> specBanners = new ArrayList<>();

    @Valid
    @NotNull
    @Builder.Default
    private List<ProductSpecDto> specs = new ArrayList<>();

    @Valid
    @NotNull
    @Builder.Default
    private List<ProductReviewDto> reviews = new ArrayList<>();
}
