package com.apsonic.backend.modules.news.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;

@Entity
@Table(name = "news_sections")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NewsSection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "news_id", nullable = false)
    private News news;

    @Enumerated(EnumType.STRING)
    @Column(name = "section_type", nullable = false, length = 50)
    private SectionType sectionType;

    @Column(name = "heading_level", length = 10)
    private String headingLevel; // H1, H2, H3

    @Column(name = "content_en", columnDefinition = "TEXT")
    private String contentEn;

    @Column(name = "content_fr", columnDefinition = "TEXT")
    private String contentFr;

    @Column(name = "content_zh", columnDefinition = "TEXT")
    private String contentZh;

    @Column(name = "media_url", columnDefinition = "TEXT")
    private String mediaUrl;

    @Column(name = "media_caption_en", columnDefinition = "TEXT")
    private String mediaCaptionEn;

    @Column(name = "media_caption_fr", columnDefinition = "TEXT")
    private String mediaCaptionFr;

    @Column(name = "media_caption_zh", columnDefinition = "TEXT")
    private String mediaCaptionZh;

    @Column(name = "media_alt_text", length = 500)
    private String mediaAltText;

    @Column(name = "link_url", columnDefinition = "TEXT")
    private String linkUrl;

    @Column(name = "link_text_en", length = 255)
    private String linkTextEn;

    @Column(name = "link_text_fr", length = 255)
    private String linkTextFr;

    @Column(name = "link_text_zh", length = 255)
    private String linkTextZh;

    @Builder.Default
    @Column(name = "section_order", nullable = false)
    private Integer sectionOrder = 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
