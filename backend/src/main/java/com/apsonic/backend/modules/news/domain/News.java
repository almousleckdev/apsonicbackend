package com.apsonic.backend.modules.news.domain;

import com.apsonic.backend.shared.audit.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "news")
@SQLRestriction("deleted = false")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class News extends BaseEntity {

    @Column(name = "slug", nullable = false, unique = true)
    private String slug;

    @Column(name = "title_en", nullable = false, length = 500)
    private String titleEn;

    @Column(name = "title_fr", length = 500)
    private String titleFr;

    @Column(name = "title_zh", length = 500)
    private String titleZh;

    @Column(name = "summary_en", columnDefinition = "TEXT")
    private String summaryEn;

    @Column(name = "summary_fr", columnDefinition = "TEXT")
    private String summaryFr;

    @Column(name = "summary_zh", columnDefinition = "TEXT")
    private String summaryZh;

    @Column(name = "content_html_en", columnDefinition = "TEXT")
    private String contentHtmlEn;

    @Column(name = "content_html_fr", columnDefinition = "TEXT")
    private String contentHtmlFr;

    @Column(name = "content_html_zh", columnDefinition = "TEXT")
    private String contentHtmlZh;

    @Column(name = "cover_image_url", columnDefinition = "TEXT")
    private String coverImageUrl;

    @Column(name = "category", nullable = false, length = 100)
    private String category;

    @Builder.Default
    @Column(name = "author_name", nullable = false)
    private String authorName = "APSONIC Team";

    @Builder.Default
    @Column(name = "author_role")
    private String authorRole = "Official Contributor";

    @Column(name = "author_avatar_url", columnDefinition = "TEXT")
    private String authorAvatarUrl;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    @Builder.Default
    @Column(name = "published", nullable = false)
    private Boolean published = true;

    @Builder.Default
    @Column(name = "views_count", nullable = false)
    private Long viewsCount = 0L;

    @Builder.Default
    @Column(name = "read_time_minutes", nullable = false)
    private Integer readTimeMinutes = 5;

    @Builder.Default
    @OneToMany(mappedBy = "news", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("sectionOrder ASC")
    private List<NewsSection> sections = new ArrayList<>();

    public void addSection(NewsSection section) {
        sections.add(section);
        section.setNews(this);
    }

    public void removeSection(NewsSection section) {
        sections.remove(section);
        section.setNews(null);
    }

    public void clearSections() {
        for (NewsSection s : new ArrayList<>(sections)) {
            removeSection(s);
        }
    }
}
