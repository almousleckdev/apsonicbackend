package com.apsonic.backend.modules.product.service;

import com.apsonic.backend.modules.product.dto.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface ProductService {
    Page<ProductListItemResponse> getPublicProducts(
            String categorySlug,
            Integer displacement,
            String brand,
            String search,
            Pageable pageable
    );

    List<ProductListItemResponse> getFeaturedProducts();

    List<Integer> getAvailableDisplacements();

    ProductDetailResponse getProductByIdOrSlug(String idOrSlug, String locale);

    Page<ProductListItemResponse> getAllProductsForAdmin(
            String categorySlug,
            String brand,
            String search,
            Pageable pageable
    );

    ProductDetailResponse createProduct(CreateProductRequest request);

    ProductDetailResponse updateProduct(String idOrSlug, UpdateProductRequest request);

    void deleteProduct(String idOrSlug);

    ProductDetailResponse toggleActiveStatus(String idOrSlug, Boolean active);

    ProductDetailResponse toggleFeaturedStatus(String idOrSlug, Boolean featured);
}
