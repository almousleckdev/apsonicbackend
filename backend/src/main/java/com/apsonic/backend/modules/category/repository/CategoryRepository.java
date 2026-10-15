package com.apsonic.backend.modules.category.repository;

import com.apsonic.backend.modules.category.domain.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {

    Optional<Category> findBySlug(String slug);

    boolean existsBySlug(String slug);

    /**
     * Whether the slug is taken by any category, including soft-deleted ones. The unique index on {@code slug} also
     * covers deleted rows, so {@link #existsBySlug} (which hides them) is not enough to avoid a constraint violation.
     */
    @Query(value = "SELECT EXISTS (SELECT 1 FROM categories WHERE slug = :slug)", nativeQuery = true)
    boolean slugTakenIncludingDeleted(@Param("slug") String slug);

    List<Category> findByActiveTrueOrderByDisplayOrderAsc();

    List<Category> findByBrandIgnoreCaseAndActiveTrueOrderByDisplayOrderAsc(String brand);

    List<Category> findAllByOrderByDisplayOrderAsc();
}
