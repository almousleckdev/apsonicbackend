-- V19__create_cms_pages_and_sections_tables.sql

-- 1. CMS Pages Table
CREATE TABLE cms_pages (
    id BIGSERIAL PRIMARY KEY,
    slug VARCHAR(200) NOT NULL UNIQUE,
    title_en VARCHAR(300) NOT NULL,
    title_fr VARCHAR(300),
    title_zh VARCHAR(300),
    status VARCHAR(50) NOT NULL DEFAULT 'DRAFT', -- DRAFT, PUBLISHED, ARCHIVED
    nav_label_en VARCHAR(200),
    nav_label_fr VARCHAR(200),
    nav_label_zh VARCHAR(200),
    nav_visibility BOOLEAN NOT NULL DEFAULT FALSE,
    nav_position VARCHAR(100), -- HEADER_MAIN, FOOTER_LEGAL, etc.
    nav_order INT NOT NULL DEFAULT 0,
    seo_title_en VARCHAR(300),
    seo_title_fr VARCHAR(300),
    seo_title_zh VARCHAR(300),
    seo_description_en TEXT,
    seo_description_fr TEXT,
    seo_description_zh TEXT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) DEFAULT 'SYSTEM',
    updated_by VARCHAR(100) DEFAULT 'SYSTEM'
);

CREATE INDEX idx_cms_pages_slug ON cms_pages(slug);
CREATE INDEX idx_cms_pages_status ON cms_pages(status);
CREATE INDEX idx_cms_pages_nav_visibility ON cms_pages(nav_visibility, nav_position, nav_order);
CREATE INDEX idx_cms_pages_deleted ON cms_pages(deleted);

-- 2. CMS Page Sections Table
CREATE TABLE cms_page_sections (
    id BIGSERIAL PRIMARY KEY,
    page_id BIGINT NOT NULL REFERENCES cms_pages(id) ON DELETE CASCADE,
    section_type VARCHAR(100) NOT NULL, -- HERO, RICH_TEXT, IMAGE_TEXT, etc.
    section_order INT NOT NULL DEFAULT 0,
    configuration JSONB NOT NULL DEFAULT '{}'::jsonb,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) DEFAULT 'SYSTEM',
    updated_by VARCHAR(100) DEFAULT 'SYSTEM'
);

CREATE INDEX idx_cms_page_sections_page_id ON cms_page_sections(page_id);
CREATE INDEX idx_cms_page_sections_order ON cms_page_sections(page_id, section_order);
CREATE INDEX idx_cms_page_sections_deleted ON cms_page_sections(deleted);
