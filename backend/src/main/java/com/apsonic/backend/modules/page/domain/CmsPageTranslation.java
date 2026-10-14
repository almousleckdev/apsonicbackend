package com.apsonic.backend.modules.page.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "cms_page_translations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CmsPageTranslation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "page_id", nullable = false)
    private CmsPage page;

    @Column(nullable = false, length = 10)
    private String locale;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(name = "nav_label", length = 200)
    private String navLabel;

    @Column(name = "seo_title", length = 300)
    private String seoTitle;

    @Column(name = "seo_description", columnDefinition = "TEXT")
    private String seoDescription;
}
