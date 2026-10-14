package com.apsonic.backend.modules.product.dto;

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
public class ProductDetailResponse {
    private Long id;
    private String slug;
    private String modelName;
    private String brand;
    private String categorySlug;
    private String categoryName;
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
    private Long viewsCount;

    @Builder.Default
    private List<ProductColorDto> colors = new ArrayList<>();

    @Builder.Default
    private List<ProductSpecBannerDto> specBanners = new ArrayList<>();

    @Builder.Default
    private List<ProductSpecDto> specs = new ArrayList<>();

    @Builder.Default
    private List<ProductReviewDto> reviews = new ArrayList<>();
}
