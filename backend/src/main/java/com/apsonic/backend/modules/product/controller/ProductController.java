package com.apsonic.backend.modules.product.controller;

import com.apsonic.backend.modules.product.dto.ProductDetailResponse;
import com.apsonic.backend.modules.product.dto.ProductListItemResponse;
import com.apsonic.backend.modules.product.service.ProductService;
import com.apsonic.backend.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
@Tag(name = "Products", description = "Public endpoints for browsing products catalog and product details")
public class ProductController {

    private final ProductService productService;

    @GetMapping
    @Operation(summary = "Get paginated products catalog", description = "Search & filter active products by category, displacement, brand, or query")
    public ResponseEntity<ApiResponse<Page<ProductListItemResponse>>> getProducts(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Integer displacement,
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "displayOrder", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        Page<ProductListItemResponse> products = productService.getPublicProducts(
                category, displacement, brand, search, pageable
        );
        return ResponseEntity.ok(ApiResponse.success("Products retrieved successfully", products));
    }

    @GetMapping("/featured")
    @Operation(summary = "Get featured products for homepage & showcase")
    public ResponseEntity<ApiResponse<List<ProductListItemResponse>>> getFeaturedProducts() {
        List<ProductListItemResponse> featured = productService.getFeaturedProducts();
        return ResponseEntity.ok(ApiResponse.success("Featured products retrieved successfully", featured));
    }

    @GetMapping("/displacements")
    @Operation(summary = "Get available engine displacements list (e.g. 110, 125, 150, 200, 250)")
    public ResponseEntity<ApiResponse<List<Integer>>> getDisplacements() {
        List<Integer> displacements = productService.getAvailableDisplacements();
        return ResponseEntity.ok(ApiResponse.success("Displacements retrieved successfully", displacements));
    }

    @GetMapping("/{idOrSlug}")
    @Operation(summary = "Get full product detail with all sections, colors, specs, and reviews")
    public ResponseEntity<ApiResponse<ProductDetailResponse>> getProductDetail(
            @PathVariable String idOrSlug,
            @RequestParam(required = false, defaultValue = "en") String locale
    ) {
        ProductDetailResponse product = productService.getProductByIdOrSlug(idOrSlug, locale);
        return ResponseEntity.ok(ApiResponse.success("Product details retrieved successfully", product));
    }
}
