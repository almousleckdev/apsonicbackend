package com.apsonic.backend.modules.product.domain;

import com.apsonic.backend.modules.category.domain.Category;
import com.apsonic.backend.shared.audit.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "products", indexes = {
        @Index(name = "idx_products_slug", columnList = "slug", unique = true),
        @Index(name = "idx_products_category", columnList = "category_id"),
        @Index(name = "idx_products_brand", columnList = "brand"),
        @Index(name = "idx_products_displacement", columnList = "displacement"),
        @Index(name = "idx_products_active", columnList = "active"),
        @Index(name = "idx_products_featured", columnList = "featured")
})
@org.hibernate.annotations.SQLRestriction("deleted = false")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product extends BaseEntity {

    @Column(nullable = false, unique = true, length = 200)
    private String slug;

    @Column(name = "model_name", nullable = false, length = 200)
    private String modelName;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(nullable = false, length = 100)
    @Builder.Default
    private String brand = "apsonic";

    private Integer displacement;

    @Column(name = "cover_image_url", nullable = false, columnDefinition = "TEXT")
    private String coverImageUrl;

    @Column(name = "hero_image_url", columnDefinition = "TEXT")
    private String heroImageUrl;

    @Column(name = "performance_image_url", columnDefinition = "TEXT")
    private String performanceImageUrl;

    @Column(name = "story_title", length = 300)
    private String storyTitle;

    @Column(name = "story_description", columnDefinition = "TEXT")
    private String storyDescription;

    @Column(name = "story_background_image", columnDefinition = "TEXT")
    private String storyBackgroundImage;

    @Column(name = "aesthetic_title", length = 300)
    private String aestheticTitle;

    @Column(name = "aesthetic_subtitle", length = 300)
    private String aestheticSubtitle;

    @Column(name = "aesthetic_bg_image", columnDefinition = "TEXT")
    private String aestheticBgImage;

    @Column(name = "aesthetic_image_1", columnDefinition = "TEXT")
    private String aestheticImage1;

    @Column(name = "aesthetic_image_2", columnDefinition = "TEXT")
    private String aestheticImage2;

    @Column(nullable = false)
    @Builder.Default
    private Boolean featured = false;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(name = "display_order", nullable = false)
    @Builder.Default
    private Integer displayOrder = 0;

    @Column(name = "views_count", nullable = false)
    @Builder.Default
    private Long viewsCount = 0L;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC")
    @Builder.Default
    private List<ProductColor> colors = new ArrayList<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC")
    @Builder.Default
    private List<ProductSpecBanner> specBanners = new ArrayList<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC")
    @Builder.Default
    private List<ProductSpec> specs = new ArrayList<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    @Builder.Default
    private List<ProductReview> reviews = new ArrayList<>();

    public void addColor(ProductColor color) {
        colors.add(color);
        color.setProduct(this);
    }

    public void addSpecBanner(ProductSpecBanner banner) {
        specBanners.add(banner);
        banner.setProduct(this);
    }

    public void addSpec(ProductSpec spec) {
        specs.add(spec);
        spec.setProduct(this);
    }

    public void addReview(ProductReview review) {
        reviews.add(review);
        review.setProduct(this);
    }

    public void clearColors() {
        colors.clear();
    }

    public void clearSpecBanners() {
        specBanners.clear();
    }

    public void clearSpecs() {
        specs.clear();
    }

    public void clearReviews() {
        reviews.clear();
    }
}
