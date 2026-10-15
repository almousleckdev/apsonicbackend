package com.apsonic.backend.modules.page.repository;

import com.apsonic.backend.modules.page.domain.CmsPageRevision;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CmsPageRevisionRepository extends JpaRepository<CmsPageRevision, Long> {

    Optional<CmsPageRevision> findTopByPageIdOrderByRevisionNumberDesc(Long pageId);

    Optional<CmsPageRevision> findByPageIdAndRevisionNumber(Long pageId, int revisionNumber);

    List<CmsPageRevision> findByPageIdOrderByRevisionNumberDesc(Long pageId);

    /** Drops the oldest draft-save revisions beyond {@code keep}; publish, rollback and creation revisions stay. */
    @Modifying
    @Query(value = "DELETE FROM cms_page_revisions WHERE id IN (SELECT id FROM cms_page_revisions "
            + "WHERE page_id = :pageId AND kind = 'DRAFT' ORDER BY revision_number DESC OFFSET :keep)", nativeQuery = true)
    void pruneDraftRevisions(@Param("pageId") Long pageId, @Param("keep") int keep);
}
