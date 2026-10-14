package com.apsonic.backend.modules.category.service;

import com.apsonic.backend.modules.category.dto.CategoryResponse;
import com.apsonic.backend.modules.category.dto.CreateCategoryRequest;
import com.apsonic.backend.modules.category.dto.UpdateCategoryRequest;

import java.util.List;

public interface CategoryService {
    List<CategoryResponse> getPublicCategories(String brand, String locale);
    List<CategoryResponse> getAllCategoriesForAdmin();
    CategoryResponse getCategoryBySlug(String slug, String locale);
    CategoryResponse createCategory(CreateCategoryRequest request);
    CategoryResponse updateCategory(String idOrSlug, UpdateCategoryRequest request);
    void deleteCategory(String idOrSlug);
    CategoryResponse toggleActiveStatus(String idOrSlug, Boolean active);
}
