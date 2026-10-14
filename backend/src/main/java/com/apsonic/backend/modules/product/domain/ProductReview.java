package com.apsonic.backend.modules.product.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "product_reviews")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    @JsonIgnore
    private Product product;

    @Column(name = "author_name", nullable = false, length = 150)
    private String authorName;

    @Column(nullable = false)
    @Builder.Default
    private Integer rating = 5;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String comment;

    @Column(name = "review_date", length = 100)
    private String reviewDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
