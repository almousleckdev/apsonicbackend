package com.apsonic.backend.modules.category.controller;

import com.apsonic.backend.modules.category.dto.CategoryResponse;
import com.apsonic.backend.modules.category.dto.CreateCategoryRequest;
import com.apsonic.backend.modules.category.dto.UpdateCategoryRequest;
import com.apsonic.backend.modules.category.service.CategoryService;
import com.apsonic.backend.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/categories")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'EDITOR')")
@Tag(name = "Admin Categories", description = "Admin management endpoints for product categories")
public class AdminCategoryController {

    private final CategoryService categoryService;

    @GetMapping
    @Operation(summary = "Get all categories for admin (active and inactive)")
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> getAllCategories() {
        List<CategoryResponse> categories = categoryService.getAllCategoriesForAdmin();
        return ResponseEntity.ok(ApiResponse.success("Categories retrieved successfully", categories));
    }

    @PostMapping
    @Operation(summary = "Create a new product category")
    public ResponseEntity<ApiResponse<CategoryResponse>> createCategory(
            @Valid @RequestBody CreateCategoryRequest request
    ) {
        CategoryResponse category = categoryService.createCategory(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Category created successfully", category));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update product category details")
    public ResponseEntity<ApiResponse<CategoryResponse>> updateCategory(
            @PathVariable String id,
            @Valid @RequestBody UpdateCategoryRequest request
    ) {
        CategoryResponse category = categoryService.updateCategory(id, request);
        return ResponseEntity.ok(ApiResponse.success("Category updated successfully", category));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete product category")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteCategory(@PathVariable String id) {
        categoryService.deleteCategory(id);
        return ResponseEntity.ok(ApiResponse.success("Category deleted successfully", null));
    }

    @PatchMapping("/{id}/toggle-active")
    @Operation(summary = "Toggle category active status")
    public ResponseEntity<ApiResponse<CategoryResponse>> toggleActive(
            @PathVariable String id,
            @RequestParam(required = false) Boolean active
    ) {
        CategoryResponse category = categoryService.toggleActiveStatus(id, active);
        return ResponseEntity.ok(ApiResponse.success("Category status updated successfully", category));
    }
}
