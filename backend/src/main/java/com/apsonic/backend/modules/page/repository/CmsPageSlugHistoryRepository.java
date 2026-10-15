package com.apsonic.backend.modules.page.repository;

import com.apsonic.backend.modules.page.domain.CmsPageSlugHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CmsPageSlugHistoryRepository extends JpaRepository<CmsPageSlugHistory, Long> {

    Optional<CmsPageSlugHistory> findBySlug(String slug);

    boolean existsBySlugAndPageIdNot(String slug, Long pageId);
}
