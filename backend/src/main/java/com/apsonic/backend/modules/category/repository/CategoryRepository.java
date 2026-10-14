package com.apsonic.backend.modules.category.repository;

import com.apsonic.backend.modules.category.domain.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {

    Optional<Category> findBySlug(String slug);

    boolean existsBySlug(String slug);

    List<Category> findByActiveTrueOrderByDisplayOrderAsc();

    List<Category> findByBrandIgnoreCaseAndActiveTrueOrderByDisplayOrderAsc(String brand);

    List<Category> findAllByOrderByDisplayOrderAsc();
}
