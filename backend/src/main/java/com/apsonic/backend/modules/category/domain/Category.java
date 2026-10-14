package com.apsonic.backend.modules.category.domain;

import com.apsonic.backend.shared.audit.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "categories", indexes = {
        @Index(name = "idx_categories_slug", columnList = "slug", unique = true),
        @Index(name = "idx_categories_brand", columnList = "brand"),
        @Index(name = "idx_categories_active", columnList = "active"),
        @Index(name = "idx_categories_parent_type", columnList = "parent_type")
})
@SQLRestriction("deleted = false")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Category extends BaseEntity {

    @Column(nullable = false, unique = true, length = 100)
    private String slug;

    @Column(name = "name_en", nullable = false, length = 200)
    private String nameEn;

    @Column(name = "name_fr", length = 200)
    private String nameFr;

    @Column(name = "name_zh", length = 200)
    private String nameZh;

    @Column(name = "image_url", columnDefinition = "TEXT")
    private String imageUrl;

    @Column(nullable = false, length = 100)
    @Builder.Default
    private String brand = "apsonic";

    @Column(name = "parent_type", length = 50)
    @Builder.Default
    private String parentType = "two-wheeler";

    @Column(name = "image_scale")
    @Builder.Default
    private Double imageScale = 1.0;

    @Column(name = "image_translate_y")
    @Builder.Default
    private Integer imageTranslateY = 0;

    @Column(name = "display_order", nullable = false)
    @Builder.Default
    private Integer displayOrder = 0;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;
}
