package com.apsonic.backend.modules.product.repository;

import com.apsonic.backend.modules.product.domain.Product;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    Optional<Product> findBySlug(String slug);

    boolean existsBySlug(String slug);

    @EntityGraph(attributePaths = {"category"})
    @Query("SELECT p FROM Product p WHERE p.id = :id")
    Optional<Product> findByIdWithDetails(@Param("id") Long id);

    @Override
    @EntityGraph(attributePaths = {"category"})
    org.springframework.data.domain.Page<Product> findAll(@org.springframework.lang.Nullable org.springframework.data.jpa.domain.Specification<Product> spec, org.springframework.data.domain.Pageable pageable);

    @EntityGraph(attributePaths = {"category"})
    @Query("SELECT p FROM Product p WHERE LOWER(p.slug) = LOWER(:slug)")
    Optional<Product> findBySlugWithDetails(@Param("slug") String slug);

    @EntityGraph(attributePaths = {"category"})
    List<Product> findByFeaturedTrueAndActiveTrueOrderByDisplayOrderAsc();

    @Query("SELECT DISTINCT p.displacement FROM Product p WHERE p.displacement IS NOT NULL AND p.active = true ORDER BY p.displacement ASC")
    List<Integer> findDistinctDisplacements();

    @Modifying
    @Query("UPDATE Product p SET p.viewsCount = p.viewsCount + 1 WHERE p.id = :id")
    void incrementViewsCount(@Param("id") Long id);
}
