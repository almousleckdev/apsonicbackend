package com.apsonic.backend.modules.category.service.impl;

import com.apsonic.backend.modules.category.domain.Category;
import com.apsonic.backend.modules.category.dto.CategoryResponse;
import com.apsonic.backend.modules.category.dto.CreateCategoryRequest;
import com.apsonic.backend.modules.category.dto.UpdateCategoryRequest;
import com.apsonic.backend.modules.category.mapper.CategoryMapper;
import com.apsonic.backend.modules.category.repository.CategoryRepository;
import com.apsonic.backend.modules.category.service.CategoryService;
import com.apsonic.backend.shared.exception.DuplicateResourceException;
import com.apsonic.backend.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;
    private final CategoryMapper categoryMapper;

    @Override
    @Transactional(readOnly = true)
    public List<CategoryResponse> getPublicCategories(String brand, String locale) {
        List<Category> categories;
        if (brand != null && !brand.isBlank()) {
            categories = categoryRepository.findByBrandIgnoreCaseAndActiveTrueOrderByDisplayOrderAsc(brand.trim());
        } else {
            categories = categoryRepository.findByActiveTrueOrderByDisplayOrderAsc();
        }
        return categories.stream()
                .map(cat -> categoryMapper.toResponse(cat, locale))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoryResponse> getAllCategoriesForAdmin() {
        return categoryRepository.findAllByOrderByDisplayOrderAsc()
                .stream()
                .map(cat -> categoryMapper.toResponse(cat, "en"))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CategoryResponse getCategoryBySlug(String slug, String locale) {
        Category category = categoryRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Category", "slug", slug));
        return categoryMapper.toResponse(category, locale);
    }

    @Override
    @Transactional
    public CategoryResponse createCategory(CreateCategoryRequest request) {
        String cleanSlug = request.getSlug().trim().toLowerCase();
        if (categoryRepository.existsBySlug(cleanSlug)) {
            throw new DuplicateResourceException("Category", "slug", cleanSlug);
        }

        Category entity = categoryMapper.toEntity(request);
        Category saved = categoryRepository.save(entity);
        log.info("Created category with ID: {}, slug: {}", saved.getId(), saved.getSlug());
        return categoryMapper.toResponse(saved, "en");
    }

    @Override
    @Transactional
    public CategoryResponse updateCategory(String idOrSlug, UpdateCategoryRequest request) {
        Category category = findCategoryEntity(idOrSlug);

        if (request.getSlug() != null && !request.getSlug().isBlank()) {
            String newSlug = request.getSlug().trim().toLowerCase();
            if (!newSlug.equals(category.getSlug()) && categoryRepository.existsBySlug(newSlug)) {
                throw new DuplicateResourceException("Category", "slug", newSlug);
            }
            category.setSlug(newSlug);
        }

        if (request.getNameEn() != null) category.setNameEn(request.getNameEn());
        if (request.getNameFr() != null) category.setNameFr(request.getNameFr());
        if (request.getNameZh() != null) category.setNameZh(request.getNameZh());
        if (request.getImageUrl() != null) category.setImageUrl(request.getImageUrl());
        if (request.getBrand() != null) category.setBrand(request.getBrand().trim().toLowerCase());
        if (request.getParentType() != null) category.setParentType(request.getParentType().trim().toLowerCase());
        if (request.getImageScale() != null) category.setImageScale(request.getImageScale());
        if (request.getImageTranslateY() != null) category.setImageTranslateY(request.getImageTranslateY());
        if (request.getDisplayOrder() != null) category.setDisplayOrder(request.getDisplayOrder());
        if (request.getActive() != null) category.setActive(request.getActive());

        Category updated = categoryRepository.save(category);
        log.info("Updated category ID: {}", updated.getId());
        return categoryMapper.toResponse(updated, "en");
    }

    @Override
    @Transactional
    public void deleteCategory(String idOrSlug) {
        Category category = findCategoryEntity(idOrSlug);
        category.setDeleted(true);
        category.setDeletedAt(java.time.Instant.now());
        category.setDeletedBy(com.apsonic.backend.shared.util.SecurityUtils.getCurrentUsername().orElse("system"));
        categoryRepository.save(category);
        log.info("Soft deleted category ID: {}", category.getId());
    }

    @Override
    @Transactional
    public CategoryResponse toggleActiveStatus(String idOrSlug, Boolean active) {
        Category category = findCategoryEntity(idOrSlug);
        boolean targetStatus = active != null ? active : !Boolean.TRUE.equals(category.getActive());
        category.setActive(targetStatus);
        Category saved = categoryRepository.save(category);
        return categoryMapper.toResponse(saved, "en");
    }

    private Category findCategoryEntity(String idOrSlug) {
        if (idOrSlug == null || idOrSlug.trim().isEmpty()) {
            throw new ResourceNotFoundException("Category", "identifier", "empty");
        }
        String clean = idOrSlug.trim();
        if (clean.matches("\\d+")) {
            Long id = Long.parseLong(clean);
            return categoryRepository.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Category", "id", clean));
        } else {
            return categoryRepository.findBySlug(clean.toLowerCase())
                    .orElseThrow(() -> new ResourceNotFoundException("Category", "slug", clean));
        }
    }
}
