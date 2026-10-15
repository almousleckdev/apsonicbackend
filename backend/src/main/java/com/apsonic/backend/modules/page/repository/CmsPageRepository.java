package com.apsonic.backend.modules.page.repository;

import com.apsonic.backend.modules.page.domain.CmsPage;
import com.apsonic.backend.modules.page.domain.PageStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface CmsPageRepository extends JpaRepository<CmsPage, Long>, JpaSpecificationExecutor<CmsPage> {

    Optional<CmsPage> findBySlugAndStatus(String slug, PageStatus status);

    List<CmsPage> findAllByIdIn(Collection<Long> ids);

    /**
     * True when a page - live or soft-deleted - already owns the slug. Native on purpose: the entity filter hides
     * soft-deleted rows but the unique index still covers them, so ignoring them would end in a constraint error.
     */
    @Query(value = "SELECT COUNT(*) > 0 FROM cms_pages WHERE slug = :slug AND (:excludeId IS NULL OR id <> :excludeId)",
            nativeQuery = true)
    boolean slugTakenIncludingDeleted(@Param("slug") String slug, @Param("excludeId") Long excludeId);
}
