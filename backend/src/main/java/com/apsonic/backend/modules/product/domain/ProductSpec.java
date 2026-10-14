package com.apsonic.backend.modules.product.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "product_specs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductSpec {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    @JsonIgnore
    private Product product;

    @Column(name = "spec_key", nullable = false, length = 100)
    private String specKey;

    @Column(name = "spec_value", nullable = false, length = 255)
    private String specValue;

    @Column(name = "spec_group", nullable = false, length = 100)
    @Builder.Default
    private String specGroup = "ENGINE";

    @Column(length = 50)
    private String unit;

    @Column(name = "is_key_metric", nullable = false)
    @Builder.Default
    private Boolean isKeyMetric = false;

    @Column(name = "display_order", nullable = false)
    @Builder.Default
    private Integer displayOrder = 0;
}
