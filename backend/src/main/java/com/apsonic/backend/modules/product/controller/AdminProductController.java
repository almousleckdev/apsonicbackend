package com.apsonic.backend.modules.product.controller;

import com.apsonic.backend.modules.product.dto.CreateProductRequest;
import com.apsonic.backend.modules.product.dto.ProductDetailResponse;
import com.apsonic.backend.modules.product.dto.ProductListItemResponse;
import com.apsonic.backend.modules.product.dto.UpdateProductRequest;
import com.apsonic.backend.modules.product.service.ProductService;
import com.apsonic.backend.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/products")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'EDITOR')")
@Tag(name = "Admin Products", description = "Admin management endpoints for products and section content")
public class AdminProductController {

    private final ProductService productService;

    @GetMapping
    @Operation(summary = "Get all products for admin with pagination & filtering")
    public ResponseEntity<ApiResponse<Page<ProductListItemResponse>>> getAllProducts(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "displayOrder", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        Page<ProductListItemResponse> products = productService.getAllProductsForAdmin(category, brand, search, pageable);
        return ResponseEntity.ok(ApiResponse.success("Products retrieved successfully", products));
    }

    @PostMapping
    @Operation(summary = "Create a new product with sections, colors, specs, and reviews")
    public ResponseEntity<ApiResponse<ProductDetailResponse>> createProduct(
            @Valid @RequestBody CreateProductRequest request
    ) {
        ProductDetailResponse product = productService.createProduct(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Product created successfully", product));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update product details and section content")
    public ResponseEntity<ApiResponse<ProductDetailResponse>> updateProduct(
            @PathVariable String id,
            @Valid @RequestBody UpdateProductRequest request
    ) {
        ProductDetailResponse product = productService.updateProduct(id, request);
        return ResponseEntity.ok(ApiResponse.success("Product updated successfully", product));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a product")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteProduct(@PathVariable String id) {
        productService.deleteProduct(id);
        return ResponseEntity.ok(ApiResponse.success("Product deleted successfully", null));
    }

    @PatchMapping("/{id}/toggle-active")
    @Operation(summary = "Toggle product active status")
    public ResponseEntity<ApiResponse<ProductDetailResponse>> toggleActive(
            @PathVariable String id,
            @RequestParam(required = false) Boolean active
    ) {
        ProductDetailResponse product = productService.toggleActiveStatus(id, active);
        return ResponseEntity.ok(ApiResponse.success("Product status updated successfully", product));
    }

    @PatchMapping("/{id}/toggle-featured")
    @Operation(summary = "Toggle product featured status")
    public ResponseEntity<ApiResponse<ProductDetailResponse>> toggleFeatured(
            @PathVariable String id,
            @RequestParam(required = false) Boolean featured
    ) {
        ProductDetailResponse product = productService.toggleFeaturedStatus(id, featured);
        return ResponseEntity.ok(ApiResponse.success("Product featured status updated successfully", product));
    }
}
