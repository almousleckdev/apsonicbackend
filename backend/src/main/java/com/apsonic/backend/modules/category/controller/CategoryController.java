package com.apsonic.backend.modules.category.controller;

import com.apsonic.backend.modules.category.dto.CategoryResponse;
import com.apsonic.backend.modules.category.service.CategoryService;
import com.apsonic.backend.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
@Tag(name = "Product Categories", description = "Public endpoints for browsing product categories")
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    @Operation(summary = "Get all active categories", description = "Retrieve list of active product categories filtered by brand and locale")
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> getCategories(
            @RequestParam(required = false) String brand,
            @RequestParam(required = false, defaultValue = "en") String locale
    ) {
        List<CategoryResponse> categories = categoryService.getPublicCategories(brand, locale);
        return ResponseEntity.ok(ApiResponse.success("Categories retrieved successfully", categories));
    }

    @GetMapping("/{slug}")
    @Operation(summary = "Get category details by slug")
    public ResponseEntity<ApiResponse<CategoryResponse>> getCategoryBySlug(
            @PathVariable String slug,
            @RequestParam(required = false, defaultValue = "en") String locale
    ) {
        CategoryResponse category = categoryService.getCategoryBySlug(slug, locale);
        return ResponseEntity.ok(ApiResponse.success("Category retrieved successfully", category));
    }
}
