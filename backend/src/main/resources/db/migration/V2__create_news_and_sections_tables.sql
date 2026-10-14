-- 1. News Articles Main Table
CREATE TABLE news (
    id BIGSERIAL PRIMARY KEY,
    slug VARCHAR(255) NOT NULL UNIQUE,
    title_en VARCHAR(500) NOT NULL,
    title_fr VARCHAR(500),
    title_zh VARCHAR(500),
    summary_en TEXT,
    summary_fr TEXT,
    summary_zh TEXT,
    cover_image_url VARCHAR(1000),
    category VARCHAR(100) NOT NULL DEFAULT 'Corporate',
    author_name VARCHAR(255) NOT NULL DEFAULT 'APSONIC Team',
    author_role VARCHAR(255) DEFAULT 'Official Contributor',
    author_avatar_url VARCHAR(1000),
    published_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    published BOOLEAN NOT NULL DEFAULT TRUE,
    views_count BIGINT NOT NULL DEFAULT 0,
    read_time_minutes INT NOT NULL DEFAULT 5,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) DEFAULT 'SYSTEM',
    updated_by VARCHAR(100) DEFAULT 'SYSTEM'
);

CREATE INDEX idx_news_slug ON news(slug);
CREATE INDEX idx_news_category ON news(category);
CREATE INDEX idx_news_published ON news(published);
CREATE INDEX idx_news_deleted ON news(deleted);

-- 2. News Article Content Sections Table
CREATE TABLE news_sections (
    id BIGSERIAL PRIMARY KEY,
    news_id BIGINT NOT NULL REFERENCES news(id) ON DELETE CASCADE,
    -- HEADER, PARAGRAPH, IMAGE, VIDEO, QUOTE, CALLOUT, LINK_BUTTON
    section_type VARCHAR(50) NOT NULL,
    -- H1, H2, H3
    heading_level VARCHAR(10),
    content_en TEXT,
    content_fr TEXT,
    content_zh TEXT,
    media_url VARCHAR(1000),
    media_caption_en TEXT,
    media_caption_fr TEXT,
    media_caption_zh TEXT,
    media_alt_text VARCHAR(500),
    link_url VARCHAR(1000),
    link_text_en VARCHAR(255),
    link_text_fr VARCHAR(255),
    link_text_zh VARCHAR(255),
    section_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_news_sections_news_id ON news_sections(news_id);
CREATE INDEX idx_news_sections_order ON news_sections(news_id, section_order);
