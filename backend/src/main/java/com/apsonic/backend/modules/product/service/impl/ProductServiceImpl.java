package com.apsonic.backend.modules.product.service.impl;

import com.apsonic.backend.modules.category.domain.Category;
import com.apsonic.backend.modules.category.repository.CategoryRepository;
import com.apsonic.backend.modules.product.domain.Product;
import com.apsonic.backend.modules.product.dto.*;
import com.apsonic.backend.modules.product.mapper.ProductMapper;
import com.apsonic.backend.modules.product.repository.ProductRepository;
import com.apsonic.backend.modules.product.service.ProductService;
import com.apsonic.backend.shared.exception.ResourceNotFoundException;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductMapper productMapper;

    @Override
    @Transactional(readOnly = true)
    public Page<ProductListItemResponse> getPublicProducts(
            String catSlug, Integer displacement, String brand, String search, Pageable pageable
    ) {
        Specification<Product> spec = (root, query, cb) -> {
            List<Predicate> list = new ArrayList<>();
            list.add(cb.isTrue(root.get("active")));
            if (catSlug != null && !catSlug.isBlank() && !"all".equalsIgnoreCase(catSlug)) {
                list.add(cb.equal(root.get("category").get("slug"), catSlug.trim().toLowerCase()));
            }
            if (displacement != null) list.add(cb.equal(root.get("displacement"), displacement));
            if (brand != null && !brand.isBlank()) list.add(cb.equal(root.get("brand"), brand.trim().toLowerCase()));
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim().toLowerCase() + "%";
                list.add(cb.or(cb.like(cb.lower(root.get("modelName")), pattern), cb.like(cb.lower(root.get("brand")), pattern)));
            }
            return cb.and(list.toArray(new Predicate[0]));
        };
        return productRepository.findAll(spec, pageable).map(productMapper::toListItemResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductListItemResponse> getFeaturedProducts() {
        return productRepository.findByFeaturedTrueAndActiveTrueOrderByDisplayOrderAsc().stream()
                .map(productMapper::toListItemResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Integer> getAvailableDisplacements() {
        return productRepository.findDistinctDisplacements();
    }

    @Override
    @Transactional
    public ProductDetailResponse getProductByIdOrSlug(String idOrSlug, String locale) {
        Product product = idOrSlug.matches("\\d+")
                ? productRepository.findByIdWithDetails(Long.parseLong(idOrSlug))
                        .orElseThrow(() -> new ResourceNotFoundException("Product", "id", idOrSlug))
                : productRepository.findBySlugWithDetails(idOrSlug.trim().toLowerCase())
                        .orElseThrow(() -> new ResourceNotFoundException("Product", "slug", idOrSlug));

        productRepository.incrementViewsCount(product.getId());
        return productMapper.toDetailResponse(product, locale);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProductListItemResponse> getAllProductsForAdmin(
            String catSlug, String brand, String search, Pageable pageable
    ) {
        Specification<Product> spec = (root, query, cb) -> {
            List<Predicate> list = new ArrayList<>();
            if (catSlug != null && !catSlug.isBlank()) {
                list.add(cb.equal(root.get("category").get("slug"), catSlug.trim().toLowerCase()));
            }
            if (brand != null && !brand.isBlank()) list.add(cb.equal(root.get("brand"), brand.trim().toLowerCase()));
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim().toLowerCase() + "%";
                list.add(cb.or(cb.like(cb.lower(root.get("modelName")), pattern), cb.like(cb.lower(root.get("slug")), pattern)));
            }
            return cb.and(list.toArray(new Predicate[0]));
        };
        return productRepository.findAll(spec, pageable).map(productMapper::toListItemResponse);
    }

    @Override
    @Transactional
    public ProductDetailResponse createProduct(CreateProductRequest request) {
        String slug = generateUniqueSlug(request.getSlug(), request.getModelName());
        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category", "id", request.getCategoryId()));

        Product product = Product.builder()
                .slug(slug).modelName(request.getModelName()).category(category)
                .brand(request.getBrand() != null ? request.getBrand().trim().toLowerCase() : "apsonic")
                .displacement(request.getDisplacement()).coverImageUrl(request.getCoverImageUrl())
                .heroImageUrl(request.getHeroImageUrl()).performanceImageUrl(request.getPerformanceImageUrl())
                .storyTitle(request.getStoryTitle()).storyDescription(request.getStoryDescription())
                .storyBackgroundImage(request.getStoryBackgroundImage()).aestheticTitle(request.getAestheticTitle())
                .aestheticSubtitle(request.getAestheticSubtitle()).aestheticBgImage(request.getAestheticBgImage())
                .aestheticImage1(request.getAestheticImage1()).aestheticImage2(request.getAestheticImage2())
                .featured(Boolean.TRUE.equals(request.getFeatured())).active(request.getActive() != null ? request.getActive() : true)
                .displayOrder(request.getDisplayOrder() != null ? request.getDisplayOrder() : 0).viewsCount(0L)
                .build();

        mapProductChildren(product, request.getColors(), request.getSpecBanners(), request.getSpecs(), request.getReviews());
        return productMapper.toDetailResponse(productRepository.save(product), "en");
    }

    @Override
    @Transactional
    public ProductDetailResponse updateProduct(String idOrSlug, UpdateProductRequest request) {
        Product product = findProductEntity(idOrSlug, true);

        if (request.getCategoryId() != null) {
            Category category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Category", "id", request.getCategoryId()));
            product.setCategory(category);
        }

        applyProductUpdates(product, request);
        return productMapper.toDetailResponse(productRepository.save(product), "en");
    }

    @Override
    @Transactional
    public void deleteProduct(String idOrSlug) {
        Product product = findProductEntity(idOrSlug, false);
        product.setDeleted(true);
        product.setDeletedAt(java.time.Instant.now());
        product.setDeletedBy(com.apsonic.backend.shared.util.SecurityUtils.getCurrentUsername().orElse("system"));
        productRepository.save(product);
    }

    @Override
    @Transactional
    public ProductDetailResponse toggleActiveStatus(String idOrSlug, Boolean active) {
        Product product = findProductEntity(idOrSlug, false);
        product.setActive(active != null ? active : !Boolean.TRUE.equals(product.getActive()));
        return productMapper.toDetailResponse(productRepository.save(product), "en");
    }

    @Override
    @Transactional
    public ProductDetailResponse toggleFeaturedStatus(String idOrSlug, Boolean featured) {
        Product product = findProductEntity(idOrSlug, false);
        product.setFeatured(featured != null ? featured : !Boolean.TRUE.equals(product.getFeatured()));
        return productMapper.toDetailResponse(productRepository.save(product), "en");
    }

    private Product findProductEntity(String idOrSlug, boolean withDetails) {
        if (idOrSlug == null || idOrSlug.trim().isEmpty()) {
            throw new ResourceNotFoundException("Product", "identifier", "empty");
        }
        String clean = idOrSlug.trim();
        if (clean.matches("\\d+")) {
            Long id = Long.parseLong(clean);
            return withDetails
                    ? productRepository.findByIdWithDetails(id).orElseThrow(() -> new ResourceNotFoundException("Product", "id", clean))
                    : productRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Product", "id", clean));
        } else {
            String slug = clean.toLowerCase();
            return withDetails
                    ? productRepository.findBySlugWithDetails(slug).orElseThrow(() -> new ResourceNotFoundException("Product", "slug", clean))
                    : productRepository.findBySlug(slug).orElseThrow(() -> new ResourceNotFoundException("Product", "slug", clean));
        }
    }

    private void applyProductUpdates(Product p, UpdateProductRequest req) {
        if (req.getModelName() != null) p.setModelName(req.getModelName());
        if (req.getBrand() != null) p.setBrand(req.getBrand().trim().toLowerCase());
        if (req.getDisplacement() != null) p.setDisplacement(req.getDisplacement());
        if (req.getCoverImageUrl() != null) p.setCoverImageUrl(req.getCoverImageUrl());
        if (req.getHeroImageUrl() != null) p.setHeroImageUrl(req.getHeroImageUrl());
        if (req.getPerformanceImageUrl() != null) p.setPerformanceImageUrl(req.getPerformanceImageUrl());
        if (req.getStoryTitle() != null) p.setStoryTitle(req.getStoryTitle());
        if (req.getStoryDescription() != null) p.setStoryDescription(req.getStoryDescription());
        if (req.getStoryBackgroundImage() != null) p.setStoryBackgroundImage(req.getStoryBackgroundImage());
        if (req.getAestheticTitle() != null) p.setAestheticTitle(req.getAestheticTitle());
        if (req.getAestheticSubtitle() != null) p.setAestheticSubtitle(req.getAestheticSubtitle());
        if (req.getAestheticBgImage() != null) p.setAestheticBgImage(req.getAestheticBgImage());
        if (req.getAestheticImage1() != null) p.setAestheticImage1(req.getAestheticImage1());
        if (req.getAestheticImage2() != null) p.setAestheticImage2(req.getAestheticImage2());
        if (req.getFeatured() != null) p.setFeatured(req.getFeatured());
        if (req.getActive() != null) p.setActive(req.getActive());
        if (req.getDisplayOrder() != null) p.setDisplayOrder(req.getDisplayOrder());

        if (req.getColors() != null) {
            p.clearColors();
            req.getColors().forEach(c -> p.addColor(productMapper.toColorEntity(c)));
        }
        if (req.getSpecBanners() != null) {
            p.clearSpecBanners();
            req.getSpecBanners().forEach(b -> p.addSpecBanner(productMapper.toSpecBannerEntity(b)));
        }
        if (req.getSpecs() != null) {
            p.clearSpecs();
            req.getSpecs().forEach(s -> p.addSpec(productMapper.toSpecEntity(s)));
        }
        if (req.getReviews() != null) {
            p.clearReviews();
            req.getReviews().forEach(r -> p.addReview(productMapper.toReviewEntity(r)));
        }
    }

    private void mapProductChildren(Product p, List<ProductColorDto> c, List<ProductSpecBannerDto> b, List<ProductSpecDto> s, List<ProductReviewDto> r) {
        if (c != null) c.forEach(item -> p.addColor(productMapper.toColorEntity(item)));
        if (b != null) b.forEach(item -> p.addSpecBanner(productMapper.toSpecBannerEntity(item)));
        if (s != null) s.forEach(item -> p.addSpec(productMapper.toSpecEntity(item)));
        if (r != null) r.forEach(item -> p.addReview(productMapper.toReviewEntity(item)));
    }

    private String generateUniqueSlug(String customSlug, String modelName) {
        String base = (customSlug != null && !customSlug.isBlank())
                ? customSlug.trim().toLowerCase().replaceAll("[^a-z0-9]+", "-")
                : modelName.trim().toLowerCase().replaceAll("[^a-z0-9]+", "-");
        base = base.replaceAll("^-+|-+$", "");
        if (base.isEmpty()) base = "product";

        String candidate = base;
        int attempts = 0;
        while (productRepository.existsBySlug(candidate) && attempts < 10) {
            candidate = base + "-" + UUID.randomUUID().toString().substring(0, 6);
            attempts++;
        }
        return candidate;
    }
}
