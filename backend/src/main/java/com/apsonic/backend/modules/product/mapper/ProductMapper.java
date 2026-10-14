package com.apsonic.backend.modules.product.mapper;

import com.apsonic.backend.modules.product.domain.*;
import com.apsonic.backend.modules.product.dto.*;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class ProductMapper {

    public ProductListItemResponse toListItemResponse(Product product) {
        if (product == null) return null;

        String catSlug = product.getCategory() != null ? product.getCategory().getSlug() : null;
        String catName = product.getCategory() != null ? product.getCategory().getNameEn() : null;

        return ProductListItemResponse.builder()
                .id(product.getId())
                .slug(product.getSlug())
                .model(product.getModelName())
                .brand(product.getBrand())
                .category(catName)
                .categorySlug(catSlug)
                .displacement(product.getDisplacement())
                .image(product.getCoverImageUrl())
                .featured(product.getFeatured())
                .active(product.getActive())
                .displayOrder(product.getDisplayOrder())
                .viewsCount(product.getViewsCount())
                .createdAt(product.getCreatedAt())
                .build();
    }

    public ProductDetailResponse toDetailResponse(Product product, String locale) {
        if (product == null) return null;

        String catSlug = product.getCategory() != null ? product.getCategory().getSlug() : null;
        String catName = product.getCategory() != null ? product.getCategory().getNameEn() : null;

        List<ProductColorDto> colorDtos = product.getColors() != null
                ? product.getColors().stream().map(this::toColorDto).collect(Collectors.toList())
                : Collections.emptyList();

        List<ProductSpecBannerDto> bannerDtos = product.getSpecBanners() != null
                ? product.getSpecBanners().stream().map(this::toSpecBannerDto).collect(Collectors.toList())
                : Collections.emptyList();

        List<ProductSpecDto> specDtos = product.getSpecs() != null
                ? product.getSpecs().stream().map(this::toSpecDto).collect(Collectors.toList())
                : Collections.emptyList();

        List<ProductReviewDto> reviewDtos = product.getReviews() != null
                ? product.getReviews().stream().map(this::toReviewDto).collect(Collectors.toList())
                : Collections.emptyList();

        return ProductDetailResponse.builder()
                .id(product.getId())
                .slug(product.getSlug())
                .modelName(product.getModelName())
                .brand(product.getBrand())
                .categorySlug(catSlug)
                .categoryName(catName)
                .displacement(product.getDisplacement())
                .coverImageUrl(product.getCoverImageUrl())
                .heroImageUrl(product.getHeroImageUrl())
                .performanceImageUrl(product.getPerformanceImageUrl())
                .storyTitle(product.getStoryTitle())
                .storyDescription(product.getStoryDescription())
                .storyBackgroundImage(product.getStoryBackgroundImage())
                .aestheticTitle(product.getAestheticTitle())
                .aestheticSubtitle(product.getAestheticSubtitle())
                .aestheticBgImage(product.getAestheticBgImage())
                .aestheticImage1(product.getAestheticImage1())
                .aestheticImage2(product.getAestheticImage2())
                .featured(product.getFeatured())
                .active(product.getActive())
                .displayOrder(product.getDisplayOrder())
                .viewsCount(product.getViewsCount())
                .colors(colorDtos)
                .specBanners(bannerDtos)
                .specs(specDtos)
                .reviews(reviewDtos)
                .build();
    }

    public ProductColorDto toColorDto(ProductColor color) {
        if (color == null) return null;
        return ProductColorDto.builder()
                .id(color.getId())
                .colorName(color.getColorName())
                .hexCode(color.getHexCode())
                .imageUrl(color.getImageUrl())
                .displayOrder(color.getDisplayOrder())
                .build();
    }

    public ProductColor toColorEntity(ProductColorDto dto) {
        if (dto == null) return null;
        return ProductColor.builder()
                .colorName(dto.getColorName())
                .hexCode(dto.getHexCode())
                .imageUrl(dto.getImageUrl())
                .displayOrder(dto.getDisplayOrder() != null ? dto.getDisplayOrder() : 0)
                .build();
    }

    public ProductSpecBannerDto toSpecBannerDto(ProductSpecBanner banner) {
        if (banner == null) return null;
        return ProductSpecBannerDto.builder()
                .id(banner.getId())
                .imageUrl(banner.getImageUrl())
                .title(banner.getTitle())
                .description(banner.getDescription())
                .metricLabel(banner.getMetricLabel())
                .metricValue(banner.getMetricValue())
                .metricUnit(banner.getMetricUnit())
                .displayOrder(banner.getDisplayOrder())
                .build();
    }

    public ProductSpecBanner toSpecBannerEntity(ProductSpecBannerDto dto) {
        if (dto == null) return null;
        return ProductSpecBanner.builder()
                .imageUrl(dto.getImageUrl())
                .title(dto.getTitle())
                .description(dto.getDescription())
                .metricLabel(dto.getMetricLabel())
                .metricValue(dto.getMetricValue())
                .metricUnit(dto.getMetricUnit())
                .displayOrder(dto.getDisplayOrder() != null ? dto.getDisplayOrder() : 0)
                .build();
    }

    public ProductSpecDto toSpecDto(ProductSpec spec) {
        if (spec == null) return null;
        return ProductSpecDto.builder()
                .id(spec.getId())
                .specKey(spec.getSpecKey())
                .specValue(spec.getSpecValue())
                .specGroup(spec.getSpecGroup())
                .unit(spec.getUnit())
                .isKeyMetric(spec.getIsKeyMetric())
                .displayOrder(spec.getDisplayOrder())
                .build();
    }

    public ProductSpec toSpecEntity(ProductSpecDto dto) {
        if (dto == null) return null;
        return ProductSpec.builder()
                .specKey(dto.getSpecKey())
                .specValue(dto.getSpecValue())
                .specGroup(dto.getSpecGroup() != null ? dto.getSpecGroup() : "ENGINE")
                .unit(dto.getUnit())
                .isKeyMetric(dto.getIsKeyMetric() != null ? dto.getIsKeyMetric() : false)
                .displayOrder(dto.getDisplayOrder() != null ? dto.getDisplayOrder() : 0)
                .build();
    }

    public ProductReviewDto toReviewDto(ProductReview review) {
        if (review == null) return null;
        return ProductReviewDto.builder()
                .id(review.getId())
                .authorName(review.getAuthorName())
                .rating(review.getRating())
                .comment(review.getComment())
                .reviewDate(review.getReviewDate())
                .createdAt(review.getCreatedAt())
                .build();
    }

    public ProductReview toReviewEntity(ProductReviewDto dto) {
        if (dto == null) return null;
        return ProductReview.builder()
                .authorName(dto.getAuthorName())
                .rating(dto.getRating() != null ? dto.getRating() : 5)
                .comment(dto.getComment())
                .reviewDate(dto.getReviewDate())
                .build();
    }
}
