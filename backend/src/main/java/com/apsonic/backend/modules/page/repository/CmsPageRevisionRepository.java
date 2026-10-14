package com.apsonic.backend.modules.page.repository;

import com.apsonic.backend.modules.page.domain.CmsPageRevision;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CmsPageRevisionRepository extends JpaRepository<CmsPageRevision, Long> {
}
