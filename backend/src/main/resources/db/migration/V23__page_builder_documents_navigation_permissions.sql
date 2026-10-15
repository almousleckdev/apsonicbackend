-- CMS page builder: page documents, revisions, slug history, navigation menus and permissions.
--
-- Additive only. The legacy cms_page_sections / cms_page_translations tables and the nav_* / published_snapshot
-- columns of cms_pages are left in place (no longer read by the application); dropping them is a separate,
-- separately approved migration. Existing pages are converted into the new document format below.

-- ---------------------------------------------------------------------------------------------------------------
-- 1. Pages
-- ---------------------------------------------------------------------------------------------------------------
ALTER TABLE cms_pages
    ADD COLUMN title              VARCHAR(150),
    ADD COLUMN draft_document     JSONB,
    ADD COLUMN published_document JSONB,
    ADD COLUMN schema_version     INT NOT NULL DEFAULT 1,
    ADD COLUMN archived_at        TIMESTAMP WITH TIME ZONE,
    ADD COLUMN published_by       VARCHAR(100);

-- ---------------------------------------------------------------------------------------------------------------
-- 2. Legacy conversion (idempotent: only touches pages that have no draft_document yet)
-- ---------------------------------------------------------------------------------------------------------------

-- Per-locale map {"en": "...", "fr": "..."} for one key of a legacy section's translations.
CREATE FUNCTION pg_temp.legacy_loc(cfg jsonb, k text) RETURNS jsonb AS $$
    SELECT NULLIF(COALESCE(jsonb_object_agg(t.key, t.value ->> k)
                           FILTER (WHERE t.value ->> k IS NOT NULL AND t.value ->> k <> ''), '{}'::jsonb), '{}'::jsonb)
    FROM jsonb_each(COALESCE(cfg -> 'translations', '{}'::jsonb)) AS t
$$ LANGUAGE sql IMMUTABLE;

-- Converts legacy translations ({locale: {title, seoTitle, seoDescription}}) and legacy sections
-- ([{sectionType, sectionOrder, configuration}]) into a version-1 page document. Legacy types that the old
-- renderer never displayed (TWO_COLUMN, THREE_COLUMN, GALLERY, CTA, FAQ, CARDS) are not carried over.
CREATE FUNCTION pg_temp.legacy_convert(translations jsonb, sections jsonb) RETURNS jsonb AS $$
    SELECT jsonb_build_object(
        'schemaVersion', 1,
        'meta', COALESCE((
            SELECT jsonb_object_agg(l.key, jsonb_strip_nulls(jsonb_build_object(
                'title', l.value ->> 'title',
                'seoTitle', NULLIF(l.value ->> 'seoTitle', ''),
                'seoDescription', NULLIF(l.value ->> 'seoDescription', ''))))
            FROM jsonb_each(COALESCE(translations, '{}'::jsonb)) AS l
            WHERE l.key IN ('en', 'fr', 'zh')), '{}'::jsonb),
        'seo', jsonb_build_object('noIndex', false),
        'sections', COALESCE((
            SELECT jsonb_agg(converted.section ORDER BY converted.ord)
            FROM (
                SELECT (s.value ->> 'sectionOrder')::int AS ord,
                       jsonb_build_object(
                           'id', gen_random_uuid()::text,
                           'type', CASE s.value ->> 'sectionType'
                               WHEN 'HERO' THEN 'hero'
                               WHEN 'RICH_TEXT' THEN 'rich-text'
                               WHEN 'IMAGE_TEXT' THEN 'text-image'
                               WHEN 'TEXT_IMAGE' THEN 'text-image'
                               WHEN 'VIDEO' THEN 'video' END,
                           'hidden', false,
                           'style', jsonb_build_object('background', 'white', 'paddingTop', 'md',
                                                       'paddingBottom', 'md', 'width', 'normal'),
                           'content', jsonb_strip_nulls(CASE s.value ->> 'sectionType'
                               WHEN 'HERO' THEN jsonb_build_object(
                                   'heading', pg_temp.legacy_loc(s.value -> 'configuration', 'title'),
                                   'subheading', pg_temp.legacy_loc(s.value -> 'configuration', 'subtitle'),
                                   'desktopImage', CASE WHEN s.value -> 'configuration' ->> 'backgroundImage' ~* '\.(mp4|webm)$'
                                       THEN NULL ELSE NULLIF(s.value -> 'configuration' ->> 'backgroundImage', '') END,
                                   'videoUrl', CASE WHEN s.value -> 'configuration' ->> 'backgroundImage' ~* '\.(mp4|webm)$'
                                       THEN s.value -> 'configuration' ->> 'backgroundImage' ELSE NULL END,
                                   'overlay', 40, 'align', 'left', 'height', 'lg',
                                   'ctas', CASE WHEN COALESCE(s.value -> 'configuration' ->> 'ctaLink', '') <> ''
                                                 AND pg_temp.legacy_loc(s.value -> 'configuration', 'ctaText') IS NOT NULL
                                       THEN jsonb_build_array(jsonb_build_object(
                                           'label', pg_temp.legacy_loc(s.value -> 'configuration', 'ctaText'),
                                           'url', s.value -> 'configuration' ->> 'ctaLink',
                                           'variant', 'primary'))
                                       ELSE '[]'::jsonb END)
                               WHEN 'RICH_TEXT' THEN jsonb_build_object(
                                   'body', pg_temp.legacy_loc(s.value -> 'configuration', 'content'))
                               WHEN 'VIDEO' THEN jsonb_build_object(
                                   'heading', pg_temp.legacy_loc(s.value -> 'configuration', 'title'),
                                   'videoUrl', NULLIF(s.value -> 'configuration' ->> 'videoUrl', ''),
                                   'poster', NULLIF(s.value -> 'configuration' ->> 'thumbnail', ''))
                               ELSE jsonb_build_object(
                                   'heading', pg_temp.legacy_loc(s.value -> 'configuration', 'title'),
                                   'body', pg_temp.legacy_loc(s.value -> 'configuration', 'description'),
                                   'image', NULLIF(s.value -> 'configuration' ->> 'image', ''),
                                   'imagePosition', CASE WHEN s.value ->> 'sectionType' = 'TEXT_IMAGE'
                                                          OR s.value -> 'configuration' ->> 'layout' = 'text-left'
                                       THEN 'right' ELSE 'left' END) END)) AS section
                FROM jsonb_array_elements(COALESCE(sections, '[]'::jsonb)) AS s
                WHERE s.value ->> 'sectionType' IN ('HERO', 'RICH_TEXT', 'IMAGE_TEXT', 'TEXT_IMAGE', 'VIDEO')
            ) AS converted), '[]'::jsonb))
$$ LANGUAGE sql VOLATILE;

-- Draft: from the relational translations + sections.
UPDATE cms_pages p
SET draft_document = pg_temp.legacy_convert(
        (SELECT jsonb_object_agg(t.locale, jsonb_build_object('title', t.title, 'seoTitle', t.seo_title,
                                                               'seoDescription', t.seo_description))
         FROM cms_page_translations t WHERE t.page_id = p.id),
        (SELECT jsonb_agg(jsonb_build_object('sectionType', s.section_type, 'sectionOrder', s.section_order,
                                             'configuration', s.configuration))
         FROM cms_page_sections s WHERE s.page_id = p.id AND s.deleted = false))
WHERE p.draft_document IS NULL;

-- Live content: from the snapshot the old site was serving (not from the draft), for pages that were published.
UPDATE cms_pages p
SET published_document = pg_temp.legacy_convert(p.published_snapshot -> 'translations', p.published_snapshot -> 'sections')
WHERE p.status = 'PUBLISHED' AND p.published_snapshot IS NOT NULL AND p.published_document IS NULL;

UPDATE cms_pages
SET title = LEFT(COALESCE(NULLIF(draft_document -> 'meta' -> 'en' ->> 'title', ''), slug), 150)
WHERE title IS NULL;

ALTER TABLE cms_pages
    ALTER COLUMN title SET NOT NULL,
    ALTER COLUMN draft_document SET NOT NULL;

-- Page-level slug lookups ignore soft-deleted rows in JPA but the unique constraint does not; keep that explicit.
CREATE INDEX idx_cms_pages_status_slug ON cms_pages (status, slug);

-- ---------------------------------------------------------------------------------------------------------------
-- 3. Revisions
-- ---------------------------------------------------------------------------------------------------------------
ALTER TABLE cms_page_revisions
    ADD COLUMN revision_number INT,
    ADD COLUMN kind            VARCHAR(20) NOT NULL DEFAULT 'LEGACY',
    ADD COLUMN note            VARCHAR(200),
    ADD COLUMN saved_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP;

UPDATE cms_page_revisions r
SET revision_number = numbered.n, saved_at = r.created_at
FROM (SELECT id, ROW_NUMBER() OVER (PARTITION BY page_id ORDER BY created_at, id) AS n FROM cms_page_revisions) numbered
WHERE numbered.id = r.id;

-- One MIGRATION revision per converted page, so a rollback target exists in the new format.
INSERT INTO cms_page_revisions (page_id, snapshot, created_at, created_by, revision_number, kind, note, saved_at)
SELECT p.id, p.draft_document, CURRENT_TIMESTAMP, 'SYSTEM',
       COALESCE((SELECT MAX(revision_number) FROM cms_page_revisions r WHERE r.page_id = p.id), 0) + 1,
       'MIGRATION', 'Converted from the legacy page format', CURRENT_TIMESTAMP
FROM cms_pages p;

ALTER TABLE cms_page_revisions
    ALTER COLUMN revision_number SET NOT NULL,
    ADD CONSTRAINT uq_cms_page_revisions_number UNIQUE (page_id, revision_number);

-- ---------------------------------------------------------------------------------------------------------------
-- 4. Slug history (old URLs of published pages keep working through a redirect)
-- ---------------------------------------------------------------------------------------------------------------
CREATE TABLE cms_page_slug_history (
    id         BIGSERIAL PRIMARY KEY,
    page_id    BIGINT       NOT NULL REFERENCES cms_pages (id) ON DELETE CASCADE,
    slug       VARCHAR(200) NOT NULL UNIQUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_cms_page_slug_history_page ON cms_page_slug_history (page_id);

-- ---------------------------------------------------------------------------------------------------------------
-- 5. Navigation menus (independent of page content; items reference pages by id)
-- ---------------------------------------------------------------------------------------------------------------
CREATE TABLE cms_navigation_menus (
    id              BIGSERIAL PRIMARY KEY,
    menu_key        VARCHAR(20) NOT NULL UNIQUE,
    draft_items     JSONB       NOT NULL DEFAULT '[]'::jsonb,
    published_items JSONB       NOT NULL DEFAULT '[]'::jsonb,
    published_at    TIMESTAMP WITH TIME ZONE,
    published_by    VARCHAR(100),
    version         BIGINT      NOT NULL DEFAULT 0,
    deleted         BOOLEAN     NOT NULL DEFAULT FALSE,
    deleted_at      TIMESTAMP WITH TIME ZONE,
    deleted_by      VARCHAR(100),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      VARCHAR(100) DEFAULT 'SYSTEM',
    updated_by      VARCHAR(100) DEFAULT 'SYSTEM'
);

-- Header = the items the site has always shown, plus any page the old builder had placed in the navigation.
-- published_at stays NULL ("not configured yet") unless legacy navigation pages exist, so until an administrator
-- publishes a menu the public site keeps using its built-in navigation unchanged.
WITH legacy AS (
    SELECT p.id, p.nav_position, p.nav_order,
           (SELECT jsonb_object_agg(t.locale, COALESCE(NULLIF(t.nav_label, ''), t.title))
            FROM cms_page_translations t WHERE t.page_id = p.id AND t.locale IN ('en', 'fr', 'zh')) AS labels
    FROM cms_pages p
    WHERE p.nav_visibility AND p.status = 'PUBLISHED' AND p.deleted = false
),
header_defaults AS (
    SELECT jsonb_build_array(
        jsonb_build_object('id', gen_random_uuid()::text, 'kind', 'ROUTE', 'route', '/products', 'visible', true,
            'openInNewTab', false, 'labels', jsonb_build_object('en', 'Products', 'fr', 'Produits', 'zh', '产品'), 'children', '[]'::jsonb),
        jsonb_build_object('id', gen_random_uuid()::text, 'kind', 'ROUTE', 'route', '/services', 'visible', true,
            'openInNewTab', false, 'labels', jsonb_build_object('en', 'Services', 'fr', 'Services', 'zh', '服务'), 'children', '[]'::jsonb),
        jsonb_build_object('id', gen_random_uuid()::text, 'kind', 'ROUTE', 'route', '/contact', 'visible', true,
            'openInNewTab', false, 'labels', jsonb_build_object('en', 'Contact Us', 'fr', 'Contact', 'zh', '联系'), 'children', '[]'::jsonb)
    ) AS items
),
header_items AS (
    SELECT hd.items || COALESCE((
        SELECT jsonb_agg(jsonb_build_object('id', gen_random_uuid()::text, 'kind', 'PAGE', 'pageId', l.id, 'visible', true,
                                            'openInNewTab', false, 'labels', l.labels, 'children', '[]'::jsonb)
                         ORDER BY l.nav_order, l.id)
        FROM legacy l WHERE COALESCE(l.nav_position, '') NOT ILIKE 'FOOTER%' AND l.labels IS NOT NULL), '[]'::jsonb) AS items
    FROM header_defaults hd
),
footer_items AS (
    SELECT COALESCE((
        SELECT jsonb_agg(jsonb_build_object('id', gen_random_uuid()::text, 'kind', 'PAGE', 'pageId', l.id, 'visible', true,
                                            'openInNewTab', false, 'labels', l.labels, 'children', '[]'::jsonb)
                         ORDER BY l.nav_order, l.id)
        FROM legacy l WHERE COALESCE(l.nav_position, '') ILIKE 'FOOTER%' AND l.labels IS NOT NULL), '[]'::jsonb) AS items
)
INSERT INTO cms_navigation_menus (menu_key, draft_items, published_items, published_at)
SELECT 'HEADER', h.items, h.items,
       CASE WHEN EXISTS (SELECT 1 FROM legacy WHERE COALESCE(nav_position, '') NOT ILIKE 'FOOTER%') THEN CURRENT_TIMESTAMP END
FROM header_items h
UNION ALL
SELECT 'FOOTER', f.items, f.items,
       CASE WHEN EXISTS (SELECT 1 FROM legacy WHERE COALESCE(nav_position, '') ILIKE 'FOOTER%') THEN CURRENT_TIMESTAMP END
FROM footer_items f;

-- ---------------------------------------------------------------------------------------------------------------
-- 6. Permissions. ROLE_EDITOR keeps only READ_PAGES / WRITE_PAGES (granted earlier): it can draft but not publish.
-- ---------------------------------------------------------------------------------------------------------------
INSERT INTO permissions (name, description) VALUES
    ('PUBLISH_PAGES', 'Publish and unpublish CMS pages'),
    ('DELETE_PAGES', 'Archive and delete CMS pages'),
    ('RESTORE_PAGES', 'Restore archived pages and roll pages back to earlier revisions'),
    ('MANAGE_NAVIGATION', 'Edit and publish the site navigation menus')
ON CONFLICT (name) DO NOTHING;

INSERT INTO roles_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r CROSS JOIN permissions p
WHERE r.name IN ('ROLE_SUPER_ADMIN', 'ROLE_ADMIN')
  AND p.name IN ('PUBLISH_PAGES', 'DELETE_PAGES', 'RESTORE_PAGES', 'MANAGE_NAVIGATION')
ON CONFLICT (role_id, permission_id) DO NOTHING;
