package com.apsonic.backend.modules.page.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** A former URL of a page that has been live. The public site redirects it to the page's current slug. */
@Entity
@Table(name = "cms_page_slug_history")
@Getter
@Setter
public class CmsPageSlugHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "page_id", nullable = false)
    private Long pageId;

    @Column(nullable = false, unique = true, length = 200)
    private String slug;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
