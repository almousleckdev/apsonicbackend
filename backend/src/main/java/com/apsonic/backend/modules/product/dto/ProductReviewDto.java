package com.apsonic.backend.modules.product.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductReviewDto {
    private Long id;
    private String authorName;
    private Integer rating;
    private String comment;
    private String reviewDate;
    private Instant createdAt;
}
