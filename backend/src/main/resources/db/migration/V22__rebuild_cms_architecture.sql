-- 1. Modify cms_pages
ALTER TABLE cms_pages 
DROP COLUMN title_en,
DROP COLUMN title_fr,
DROP COLUMN title_zh,
DROP COLUMN nav_label_en,
DROP COLUMN nav_label_fr,
DROP COLUMN nav_label_zh,
DROP COLUMN seo_title_en,
DROP COLUMN seo_title_fr,
DROP COLUMN seo_title_zh,
DROP COLUMN seo_description_en,
DROP COLUMN seo_description_fr,
DROP COLUMN seo_description_zh,
ADD COLUMN published_snapshot JSONB,
ADD COLUMN published_at TIMESTAMP WITH TIME ZONE;

-- 2. Create cms_page_translations
CREATE TABLE cms_page_translations (
    id BIGSERIAL PRIMARY KEY,
    page_id BIGINT NOT NULL REFERENCES cms_pages(id) ON DELETE CASCADE,
    locale VARCHAR(10) NOT NULL,
    title VARCHAR(300) NOT NULL,
    nav_label VARCHAR(200),
    seo_title VARCHAR(300),
    seo_description TEXT,
    UNIQUE(page_id, locale)
);

CREATE INDEX idx_cms_page_translations_page_id ON cms_page_translations(page_id);

-- 3. Create cms_page_revisions
CREATE TABLE cms_page_revisions (
    id BIGSERIAL PRIMARY KEY,
    page_id BIGINT NOT NULL REFERENCES cms_pages(id) ON DELETE CASCADE,
    snapshot JSONB NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) DEFAULT 'SYSTEM'
);

CREATE INDEX idx_cms_page_revisions_page_id ON cms_page_revisions(page_id);
