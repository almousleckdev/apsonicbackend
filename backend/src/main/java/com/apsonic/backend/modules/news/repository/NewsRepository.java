package com.apsonic.backend.modules.news.repository;

import com.apsonic.backend.modules.news.domain.News;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface NewsRepository extends JpaRepository<News, Long> {

    Optional<News> findBySlug(String slug);

    @Query("SELECT n FROM News n LEFT JOIN FETCH n.sections WHERE n.id = :id")
    Optional<News> findByIdWithSections(@Param("id") Long id);

    @Query("SELECT n FROM News n LEFT JOIN FETCH n.sections WHERE n.slug = :slug")
    Optional<News> findBySlugWithSections(@Param("slug") String slug);

    Page<News> findByPublishedTrueOrderByPublishedAtDesc(Pageable pageable);

    Page<News> findByPublishedTrueAndCategoryIgnoreCaseOrderByPublishedAtDesc(String category, Pageable pageable);

    Page<News> findByCategoryIgnoreCaseOrderByPublishedAtDesc(String category, Pageable pageable);

    boolean existsBySlug(String slug);


    @Modifying
    @Query(value = "UPDATE news SET views_count = COALESCE(views_count, 0) + 1 WHERE id = :id", nativeQuery = true)
    void incrementViewsCountNative(@Param("id") Long id);
}
