# CMS Page Builder: Phase 1 audit and design

Status: proposed (Phase 1). Nothing in this document is implemented yet.

## 1. Audit of what exists

Backend (`modules/page`, `modules/cms`), frontend (`app/[locale]/admin/(portal)/pages`, `navigation`, `components/admin/pages`, `DynamicCmsPageViewer`).

| Area | Finding | Decision |
|---|---|---|
| Publishing | `createPage`/`updatePage` publish immediately when the request carries `status=PUBLISHED`. A save can therefore change the live site. | Rebuild. Saving never changes status; publish is its own endpoint. |
| Draft vs live | Live content is a JSON snapshot (`published_snapshot`) while the draft is spread over `cms_page_sections` + `cms_page_translations`. The separation is real but the draft shape is relational and hard to validate or version. | Keep the separation. Draft becomes one validated document. |
| Concurrency | `BaseEntity` has `@Version`, but the request carries no version, so last write wins silently. | Require `expectedVersion`; reject with 409. |
| Errors | `RuntimeException("Page not found")` (500), duplicate slug on create is a bare `RuntimeException`. | Typed exceptions, 404/409. |
| Validation | Only a slug regex. No section schema, no URL checks (only HTML is sanitized), unknown section types per the enum only, no reserved slugs. Slug uniqueness ignores soft-deleted rows (same bug class as categories). | Section registry with per-field validation; reserved + historical slugs. |
| Revisions | Rows are written on publish only; nothing reads them. No history list, no rollback. | Revision on every explicit save and publish; list + restore. |
| Preview | Admin preview page re-uses the viewer but with an unrelated desktop/mobile box (no real breakpoints). No secure URL. | iframe preview with real viewport widths + signed expiring token. |
| Section types | Enum has 11 types; the viewer renders 4 (HERO, IMAGE_TEXT, RICH_TEXT, VIDEO), others silently disappear. | Registry; each type has schema + renderer + editor. |
| Navigation | `/pages/navigation` returns flat pages by `nav_order`. The admin Navigation screen's **Save** is a stub ("UI demo"); it persists nothing. Header hardcodes Products/Services/Contact and appends flat pages. No nesting, no footer, no external links, not keyed to page ids. | Rebuild as an independent module. |
| Permissions | Controller is class-level `hasAnyRole('SUPER_ADMIN','ADMIN')`. Permissions `READ_PAGES`/`WRITE_PAGES` exist in the DB and are loaded into the JWT, but are unused here. | Add publish/delete/restore/navigation permissions; enforce with `hasAuthority`. |
| Rich text | `HtmlSanitizer` (OWASP, allow-list) already exists and is applied on save and on read. | Reuse unchanged. |
| Data | Local DB: 0 pages, nothing deployed. | Migration still converts any legacy rows, non-destructively. Old tables are kept, not dropped. |
| Reusable | `BaseEntity` (audit, soft delete, `@Version`), `HtmlSanitizer`, `ApiResponse`, JWT/permission loading, `apiClient`, dnd-kit, Toast/Modal/Button/Input, `LanguageTabs`, `CollapsibleCard`, `CompactMediaField`, the admin layout and Sidebar, the media upload endpoint. | Reuse. |

Rebuilt, not patched: the page service/controller/DTOs, the old admin pages UI, the old viewer, and the fake navigation screen. The old frontend files are deleted in the commit that replaces them (git keeps them recoverable).

## 2. Page document (schema version 1)

One JSON document per page version. The draft lives in `cms_pages.draft_document`, the live version in `cms_pages.published_document`.

```
{
  "schemaVersion": 1,
  "meta": {                       // per locale; "en" required to publish
    "en": { "title": "...", "seoTitle": "...", "seoDescription": "...",
            "ogTitle": "...", "ogDescription": "..." },
    "fr": {...}, "zh": {...}
  },
  "seo": { "canonicalUrl": "https://...", "ogImage": "/uploads/...", "noIndex": false },
  "sections": [
    { "id": "<uuid>", "type": "hero", "hidden": false,
      "style": { "background": "white|light|dark|brand", "paddingTop": "none|sm|md|lg|xl",
                 "paddingBottom": "...", "width": "narrow|normal|wide" },
      "content": { ...type specific... } }
  ]
}
```

- Section `id` is a UUID, stable across edits (needed for drag-and-drop, comments, and diffing).
- Localized text fields are `{ "en": "", "fr": "", "zh": "" }` objects. Non-text fields (images, urls, enums) are shared across locales unless the schema marks them localized.
- No HTML except in fields declared `richtext`; no arbitrary component names, no style strings, no script. Only the closed enums above can influence layout.

## 3. Section registry

Backend is authoritative. Each section type is one Java definition: `type`, `label`, `category`, and a list of fields. Field kinds:
`TEXT`, `TEXTAREA`, `RICHTEXT`, `URL` (site path or http/https/mailto/tel), `MEDIA` (`/uploads/...` or https), `ENUM`, `NUMBER`, `BOOLEAN`, `LIST` (of an item schema, with min/max). Each field carries `localized`, `required`, `maxLength`, `default`.

The same definition drives: validation of every save, defaults for new sections, and the property panel (served read-only at `GET /api/v1/admin/pages/section-types`). Adding a section type = one backend definition + one frontend renderer + an entry (icon) in the frontend registry. The builder, canvas, panel, history and validation are untouched.

Initial types: `hero` (heading, text, desktop + mobile image, optional video, overlay, alignment, up to 2 CTAs), `rich-text`, `text-image` (alignment left/right), `gallery`, `video`, `feature-grid` (icon cards), `stats`, `product-showcase` (picks categories/products by slug), `service-showcase`, `cta-banner`, `testimonials` (reuses existing testimonials content), `news-list` (latest N), `faq`, `dealers` (locations list), `contact` (details + links to the existing contact form), `spacer`.

Dynamic sections (`product-showcase`, `news-list`, `testimonials`) store only references/limits, never copies of the data.

## 4. Lifecycle, versioning and URL rules

States: `DRAFT`, `PUBLISHED`, `ARCHIVED`. A published page also carries a draft that may differ ("unpublished changes").

| Action | Effect |
|---|---|
| Create | status DRAFT, empty valid document, revision 1. |
| Save draft | Replaces `draft_document` after validation. Requires `expectedVersion`; 409 on mismatch. Writes a revision (coalesced: a save by the same user within 5 minutes updates that revision instead of adding one, to keep autosave from flooding history). Never touches live content. |
| Publish | Requires `PUBLISH_PAGES`. Re-validates strictly (English title and required English fields, no broken references). Copies draft to `published_document`, status PUBLISHED, writes a publish revision. |
| Unpublish | status DRAFT; `published_document` retained for restore; public API returns 404. |
| Archive / restore | ARCHIVED hides from lists by default, public 404; restore returns it to DRAFT. |
| Rollback | Copies a revision document into the draft (does not publish). |
| Duplicate | New page, slug `<slug>-copy[-n]`, DRAFT, same document with new section ids. |
| Delete | Soft delete; slug stays reserved. Pages referenced by navigation can't be deleted until removed from navigation (409 with the menu names). |

Slug rules: `^[a-z0-9]+(-[a-z0-9]+)*$`, 1 to 80 chars, unique across live, soft-deleted and historical slugs, not in the reserved list (`admin, api, preview, products, services, contact, news, login, uploads, categories, _next, ...`). Changing the slug of a published page records the old slug in `cms_page_slug_history`; the public API answers the old slug with a redirect target (frontend issues a permanent redirect), so links never break.

Localization: English is required to publish. FR/ZH are optional; missing text falls back to English at render time. Publish and the editor report per-locale completeness (counts of empty localized fields) as warnings, never as blockers.

## 5. Preview

- In the builder: the canvas is an iframe of the real public route in embed mode (`/{locale}/preview/pages/{id}?embed=1`). The iframe's real viewport width gives real Tailwind breakpoints at desktop / tablet (820px) / mobile (390px). The editor sends the in-memory document by `postMessage` (same-origin checked) so edits appear instantly; clicking a section in the iframe selects it.
- Shareable preview: `POST /api/v1/admin/pages/{id}/preview-token` (needs `READ_PAGES`) returns a short-lived (15 min) HMAC-signed token. `GET /api/v1/preview/pages/{id}?token=...` returns the draft with `Cache-Control: no-store`, `X-Robots-Tag: noindex`. The Next.js preview route sets `robots: noindex` and `dynamic = 'force-dynamic'`. Tokens are bound to page id, never contain the page slug, and are not accepted by any other endpoint. The signing key is a dedicated `PREVIEW_TOKEN_SECRET` env var (no default; the app refuses to start without it in production profile).
- Public API only ever reads `published_document`; the drafts column is never mapped into public DTOs (test enforces).

## 6. Navigation

Independent module. Two menus, `HEADER` and `FOOTER`, each a document `{ items: [ ... ] }` with a draft and a published copy (plus `version`), so changes can be previewed before publishing.

```
item: { id (uuid), kind: PAGE | ROUTE | EXTERNAL, pageId?, route?, url?,
        labels: { en, fr, zh }, visible, openInNewTab, children: [item] }
```
- `PAGE` references `cms_pages.id` (stable). The public endpoint resolves the current slug and drops items whose page is not published, so unpublishing never leaves a dead link.
- `ROUTE` is from an allow-list of built-in routes (`/`, `/products`, `/services`, `/contact`, `/news`); `/products` keeps the existing mega dropdown.
- `EXTERNAL` needs https/http, validated server-side.
- Validation: depth max 3, max 12 top-level and 40 total items, the same page at most once per menu, unique ids, label `en` required, safe URLs. Nesting is structural (a tree), so cycles cannot be expressed; the validator still rejects an id appearing twice.
- Seeded default header menu = today's items (Home, Products, Services, News, Contact), so the site looks identical until someone edits.
- The public header falls back to the built-in menu if the API is unreachable.

## 7. Permissions

New permissions added by migration (idempotent): `PUBLISH_PAGES`, `DELETE_PAGES`, `RESTORE_PAGES`, `MANAGE_NAVIGATION`. Existing `READ_PAGES`, `WRITE_PAGES` are used.

| Action | Permission |
|---|---|
| list, view, preview token, revisions list | `READ_PAGES` |
| create, edit draft, duplicate | `WRITE_PAGES` |
| publish, unpublish | `PUBLISH_PAGES` |
| archive/delete | `DELETE_PAGES` |
| restore archived, rollback to revision | `RESTORE_PAGES` |
| edit/publish navigation | `MANAGE_NAVIGATION` |

Grants: SUPER_ADMIN and ADMIN all. EDITOR: `READ_PAGES`, `WRITE_PAGES` only (can draft, cannot publish or change navigation). Checked server-side with `@PreAuthorize("hasAuthority(...)")`; the UI additionally hides controls but is not relied on. This is a behavior change from today (EDITOR currently has no page access at all); it needs sign-off.

## 8. API sketch (all under `/api/v1`)

Admin (`/admin/pages`): `GET` list, `POST` create, `GET /{id}`, `PUT /{id}` (save draft, `expectedVersion`), `POST /{id}/publish`, `POST /{id}/unpublish`, `POST /{id}/archive`, `POST /{id}/restore`, `POST /{id}/duplicate`, `DELETE /{id}`, `GET /{id}/revisions`, `POST /{id}/revisions/{rev}/rollback`, `POST /{id}/preview-token`, `GET /section-types`.
Admin navigation (`/admin/navigation/{menu}`): `GET` (draft + published), `PUT` (draft, `expectedVersion`), `POST /publish`.
Public: `GET /pages/slug/{slug}` (published only, returns the redirect target for historical slugs), `GET /navigation/{menu}` (resolved published menu), `GET /preview/pages/{id}?token=`.
The legacy `/pages/navigation` and `/pages/slug` shapes are replaced; the only consumers are the files being rebuilt.

## 9. Migration and data preservation

Flyway, additive: new columns (`draft_document`, `published_document`, `schema_version`, `archived_at`), new tables (`cms_navigation_menus`, `cms_page_slug_history`), new permission rows and grants, revision columns. A converter step maps legacy `cms_pages` + translations + sections (and legacy `published_snapshot`) into the new documents; unknown legacy section types become a skipped section recorded in the revision note rather than silently dropped. The old `cms_page_sections` and `cms_page_translations` tables are left in place (read-only, unused) and removed only in a later, separately approved migration. No data is deleted by these migrations. Backup before applying to any shared environment is part of the RUNBOOK.

## 10. Frontend architecture

```
lib/page-builder/        types, registry (type -> icon + renderer + defaults), document reducer
                         with undo/redo history (pure functions, unit tested), helpers
components/page-builder/
  renderer/              PageRenderer + one component per section type (public + preview)
  editor/                EditorShell, TopBar, SectionLibrary, SectionList (dnd-kit), PreviewFrame,
                         PropertiesPanel (schema-driven field editors), RevisionsDrawer, PublishDialog
components/admin/navigation/   tree editor, item form, live header/footer preview
app/[locale]/[slug]/page.tsx   public renderer (published only)
app/[locale]/preview/pages/[id]  token preview (noindex, no-store) + embed mode
```
Design: Apsonic tokens only (`brand-green #00A651` and hover, zinc neutrals, header yellow accent `#FFD700`, Geist, the existing uppercase/black heading style on the public site; admin uses the existing compact admin look). No new colors or fonts. Keyboard support: dnd-kit KeyboardSensor with announcements, `Cmd/Ctrl+Z` / `Shift+Z` undo/redo, `Cmd/Ctrl+S` save, visible focus rings, labelled controls.

Autosave: debounced draft save (2.5 s after the last change) with an explicit status chip (`Saved`, `Saving…`, `Unsaved changes`, `Conflict`, `Offline`); never publishes.

## 11. Implementation phases

1. Audit and design (this document). Delete nothing yet.
2. Backend foundation: migration, document model, section registry + validators, services, controllers, permissions, preview tokens, backend tests. Remove the old page module code in the same commit.
3. Frontend builder: registry, renderer, editor, properties panel, drag and drop, undo/redo, autosave, responsive preview. Remove the old admin pages UI and viewer.
4. Revisions, publish workflow, navigation module + header/footer integration.
5. Security tests, accessibility pass, E2E verification in the browser, production build.

## 12. Risks and open decisions

- EDITOR permission change (section 7) alters who can do what. Default proposed above.
- `PREVIEW_TOKEN_SECRET` is a new required env var; deploy files and RUNBOOK must be updated and the secret generated fresh by the operator.
- The products page still filters from static data (existing limitation), so `product-showcase` can only show categories/products that exist in the database.
- Uploads are local disk; media validation checks path shape, not file content (existing upload endpoint is unchanged).
- Services page currently lets a published CMS page with slug `services` override the built-in page; this behavior is preserved.

## Addendum: text styles and fields shared by every section

- Every section with a heading offers the same three pieces of text: a small label above (`eyebrow`), the `heading` and a subheading (`intro`; `subheading` in the hero, `text` in the call to action). Each can be given a size (50–300 %, 100 = the section's normal size), a `#rrggbb` color and one of four bundled typefaces (`default`, `serif`, `condensed`, `mono`) through shared style fields (`eyebrowSize/Color/Font`, `headingSize/Color/Font`, `subheadingSize/Color/Font`). They are validated like every other style value (closed list, range, hex color); the renderer maps them to CSS custom properties and class names, never to free text.
- Content saved before this change is upgraded on save (`SectionRegistry.upgradeContent`): the hero's old `headingSize` choice was dropped in favour of the shared percentage.
- The editor's own texts are bilingual (中文 + English); see `apsonicui/messages/README.md`.
