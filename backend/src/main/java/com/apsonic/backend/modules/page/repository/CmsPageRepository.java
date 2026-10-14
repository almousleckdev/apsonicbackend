package com.apsonic.backend.modules.page.repository;

import com.apsonic.backend.modules.page.domain.CmsPage;
import com.apsonic.backend.modules.page.domain.enums.PageStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CmsPageRepository extends JpaRepository<CmsPage, Long> {
    Optional<CmsPage> findBySlugAndStatus(String slug, PageStatus status);
    boolean existsBySlugAndIdNot(String slug, Long id);
    boolean existsBySlug(String slug);
    
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"translations", "sections"})
    List<CmsPage> findByNavVisibilityTrueAndStatusOrderByNavOrderAsc(PageStatus status);

    @Override
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"translations", "sections"})
    Page<CmsPage> findAll(Pageable pageable);

    @Override
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"translations", "sections"})
    Optional<CmsPage> findById(Long id);
}
