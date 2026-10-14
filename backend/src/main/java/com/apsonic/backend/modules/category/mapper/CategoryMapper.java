package com.apsonic.backend.modules.category.mapper;

import com.apsonic.backend.modules.category.domain.Category;
import com.apsonic.backend.modules.category.dto.CategoryResponse;
import com.apsonic.backend.modules.category.dto.CreateCategoryRequest;
import org.springframework.stereotype.Component;

@Component
public class CategoryMapper {

    public CategoryResponse toResponse(Category category, String locale) {
        if (category == null) return null;

        String lang = (locale != null) ? locale.toLowerCase() : "en";
        String resolvedName = resolveName(category, lang);

        return CategoryResponse.builder()
                .id(category.getId())
                .slug(category.getSlug())
                .name(resolvedName)
                .nameEn(category.getNameEn())
                .nameFr(category.getNameFr())
                .nameZh(category.getNameZh())
                .imageUrl(category.getImageUrl())
                .brand(category.getBrand())
                .parentType(category.getParentType() != null ? category.getParentType() : "two-wheeler")
                .imageScale(category.getImageScale())
                .imageTranslateY(category.getImageTranslateY())
                .displayOrder(category.getDisplayOrder())
                .active(category.getActive())
                .createdAt(category.getCreatedAt())
                .build();
    }

    public Category toEntity(CreateCategoryRequest request) {
        if (request == null) return null;

        return Category.builder()
                .slug(request.getSlug().trim().toLowerCase())
                .nameEn(request.getNameEn())
                .nameFr(request.getNameFr())
                .nameZh(request.getNameZh())
                .imageUrl(request.getImageUrl())
                .brand(request.getBrand() != null ? request.getBrand().trim().toLowerCase() : "apsonic")
                .parentType(request.getParentType() != null ? request.getParentType().trim().toLowerCase() : "two-wheeler")
                .imageScale(request.getImageScale() != null ? request.getImageScale() : 1.0)
                .imageTranslateY(request.getImageTranslateY() != null ? request.getImageTranslateY() : 0)
                .displayOrder(request.getDisplayOrder() != null ? request.getDisplayOrder() : 0)
                .active(request.getActive() != null ? request.getActive() : true)
                .build();
    }

    private String resolveName(Category category, String lang) {
        if ("fr".equals(lang) && category.getNameFr() != null && !category.getNameFr().isBlank()) {
            return category.getNameFr();
        }
        if ("zh".equals(lang) && category.getNameZh() != null && !category.getNameZh().isBlank()) {
            return category.getNameZh();
        }
        return category.getNameEn();
    }
}
