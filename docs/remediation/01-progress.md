# Remediation progress

| ID | Finding | Status | Commit |
|---|---|---|---|
| C1 | Unauthenticated debug endpoint | Fixed + regression test | 94d4348 |
| C2 | Default Super Admin (Flyway V1 seed + reset on every boot) | Fixed (code). Seed row is disabled at startup, not deleted | f4d7cff |
| C3 | Committed secret defaults / no JWT key validation | Fixed (code). **Rotation of existing secrets is the operator's task, not done** | a112620 |
| C4 | Next.js critical advisories | Fixed: `npm audit --omit=dev` = 0. 5 dev-only highs (eslint tooling) accepted | apsonicui ceb8c12 |
| H1 | Stored XSS via SVG upload | Fixed: content sniffing, SVG removed, `/uploads` nosniff + sandbox CSP | see git log |
| H2 | Access token in localStorage | Fixed: memory-only; session restored via httpOnly refresh cookie; verified in browser | apsonicui (latest) |
| H4 | 24h tokens, no jti/iss/aud, status not enforced, raw refresh tokens, no reuse detection | Fixed + e2e tests | 6c4fe96 |
| -  | Deactivated users kept access via refresh (new finding) | Fixed: filter + refresh check status; disabling/deleting/password reset revokes refresh tokens | 6c4fe96 |
| CSRF | Cookie endpoints relied on SameSite alone | Added header + Origin guard, explicit CORS headers | see git log |
| H5 | No rate limiting (login, refresh, contact, view counter) | Fixed: Redis limiter + filter; verified through the Next proxy | see git log |
| H6 | Actuator/Swagger public, debug-by-default profile, error detail | Fixed: prod is the default; docs/actuator locked down | see git log |
| M3 | No security headers / CSP | Fixed on API and site; nonce CSP verified in a browser | see git log |
| -  | All client errors returned 500 + stack trace | Fixed (404/405/400/415/413) | see git log |
| H3 | CMS HTML rendered unsanitized (stored XSS; the NewsContent "sanitizer" only stripped CSS classes) | Fixed: OWASP allow-list sanitizer in the backend on save AND on read (news HTML, CMS section config, published snapshots); 41 payload tests + 3 end-to-end tests | see git log |
| M4 | Flyway validation off, auto-baseline on | Fixed for prod (strict default); local profile lenient until the dev DB is recreated; drift detection tested | see git log |
| M5 | Pool 100 / 400 threads / 100MB form posts | Right-sized (pool 20, threads 200, forms 2MB; env-tunable); no load measurements yet | see git log |
| -  | Frontend production build broken | Fixed (5 type errors) | apsonicui af0c89b |

Open: none from the original High/Medium list.

## Accepted risks
- Dev-only npm advisories (`braces`/`micromatch`/`fast-glob` via `eslint-config-next`): not in the production bundle;
  npm's only "fix" is a downgrade to Next 14.
- Gatling simulations still log in with the retired `superadmin`/`Admin@123`; they need a bootstrap admin.
- Historical git objects (root repo baseline commit) contain the old default secrets. Never push that history
  to a shared remote without rotating them.

## Aliyun OSS evaluation (uploads)
Today files are written to `./uploads` on the app host. This blocks horizontal scaling, has no backup, and
puts user-controlled content on the application's origin. Recommended target (pending your approval; no cloud
resources have been created or modified):
- Private OSS bucket in the same region as the app; app uploads via the OSS Java SDK using a RAM role with a
  policy limited to `oss:PutObject`/`GetObject` on one prefix (no AccessKey in code; STS or ECS RAM role).
- Serve media from a separate cookieless domain (CDN in front of OSS) so uploaded content can never share an
  origin with the admin session; set `Content-Type` from the validated type and `X-Content-Type-Options: nosniff`.
- Keep `UploadContentValidator` in front of the SDK call; OSS does not validate content.
- Migration of the existing 101 files is a separate, reviewed step (copy, verify checksums, then switch URLs).
- Open decision: bucket/CDN domain naming and region. To be discussed with the deployment architecture.

## Deployment notes for the auth changes
- All existing sessions end once at deploy (old access tokens lack `jti`; stored refresh tokens are no longer raw).
- `JWT_ACCESS_EXPIRATION_MS` above 1 hour now fails startup. A stale 24h value in an existing `.env` must be removed.
- `CORS_ALLOWED_ORIGINS` must contain the exact public site origin(s): the CSRF guard rejects other `Origin`s on
  `/auth/refresh` and `/auth/logout`. The API must also be reachable by the browser from that origin (proxy).
- Frontend and backend must be deployed together (the frontend now sends `X-Requested-With: apsonic-web`).

## Known follow-ups
- Admin pages fire data requests before the session is restored (about 8 initial 401s, recovered by the shared
  refresh). Gate fetching on auth readiness in Phase 3.
- Multi-tab: a refresh race inside 10s is tolerated; outside it a replay revokes all sessions by design.

## Deployment requirements introduced by H5/H6/M3
- `SPRING_PROFILES_ACTIVE` now defaults to `prod`. Local development must set `local` (already in `.env.example`).
- Real client IPs for rate limiting come from `X-Forwarded-For`, honoured only from private-range peers
  (`server.forward-headers-strategy=native`). The backend must sit behind the Next.js proxy / SLB on a private
  network and must never be reachable from the internet directly, or the limits can be bypassed.
  The SLB must be configured to set/append `X-Forwarded-For`.
- Rate limits (per client IP): login 10/min, refresh 60/min, contact form 5/10 min, news view counter 20/h.
  Fail-open if Redis is down (logged at ERROR); per-account lockout still protects credentials.
- The public `POST /api/v1/news/{id}/views` endpoint is not called by the website. Consider removing it.
- The site CSP allows only the third parties currently used (Mapbox, YouTube/Google Maps embeds, Nominatim,
  GitHub raw GeoJSON, configured image hosts). A new embed or CDN needs `lib/security/csp.ts` updated.
- `style-src` still allows `'unsafe-inline'` (framer-motion/Tiptap/Mapbox need inline styles).
- HSTS is sent by both tiers; confirm the domain is HTTPS-only before the first production deploy.

## HTML sanitisation (H3)
- One policy (`shared/html/HtmlSanitizer`): text structure, links (http/https/mailto/tel, forced `rel=noopener noreferrer`),
  images, YouTube embeds, uploaded video, text-align. Everything else (scripts, handlers, forms, SVG/MathML,
  other iframes, other URL schemes, other inline styles) is removed.
- Applied on save and on read, so rows stored before the fix are safe without a migration, and it is idempotent.
- Only strings that contain markup are processed; plain text (titles, URLs, labels) is never entity-encoded.
- A new editor feature that needs more tags/attributes must extend the policy and its test corpus.
- Defence in depth: the site CSP blocks inline event handlers and eval even if a payload slipped through.

## Pool sizing (M5)
Values are conservative defaults, not measured optima: `DB_POOL_MAX=20`, `DB_POOL_MIN_IDLE=5`, 200 request threads.
Each replica adds `DB_POOL_MAX` connections; keep replicas x pool below the database connection limit. Run the
Gatling scenarios (after giving them a bootstrap admin) to tune with data before raising anything.

## Phase 3 (maintainability) - status: complete for the frontend lint/type debt
Done:
- Admin portal guarded by a real layout (`admin/(portal)/layout.tsx`): data fetching starts only after authentication.
- Service layer typed; dead `token` parameters removed; uploads go through `apiClient` (they never refreshed a
  15-minute token).
- Admin lists use TanStack Query (`hooks/queries/useRemoteList`); forms initialise from props / remount by key
  instead of copying props into state in effects.
- No explicit `any` left; frontend lint: 157 problems (90 errors) -> 40 warnings, 0 errors.
- Bugs found and fixed on the way: CMS navigation links had no label; dashboard shortcut to a missing route;
  a hook called after an early return in `NewsContent`; `notFound()` swallowed by try/catch; admin-uploaded
  hero/performance images never shown on product pages; wasted categories request on the products page.
- Dead code removed: `components/cms` renderer, 20 unreferenced frontend files, 3 unused dependencies, 4 empty
  backend placeholder modules. README rewritten.
- Frontend tests: Vitest + jsdom, 18 tests (session handling, CSRF header, CSP, storage hygiene).
Remaining:
- 40 lint warnings (unused variables, 5 exhaustive-deps, 2 `<img>`), 42 unused exports.
- Duplicated section-type enums on the backend; the backend has not had a maintainability pass beyond security work.
- Component tests and E2E (Phase 4).

## Phase 5 (production readiness) - status
Prepared and tested locally (NOT yet run on any server, no cloud resource was touched):
- `backend/Dockerfile`, `apsonicui/Dockerfile`: multi-stage, non-root (uid 10001), health checks, layered jar, Node 22 LTS
  (Node 20 is end-of-life), npm removed from the runtime image. Images: backend 427 MB, frontend ~1 GB (734 MB product media).
- `deploy/`: production compose (only Caddy publishes ports; Postgres/Redis/backend on an internal network without
  internet; read-only frontend; memory limits; `noeviction` Redis so revocations are never silently evicted), Caddyfile
  (automatic HTTPS), bootstrap/SSH-hardening scripts, deploy with automatic rollback, backup and restore scripts, RUNBOOK.md.
- Verified against the running stack: login/refresh/upload through Caddy -> Next -> backend; rate limiting keys on the real
  client address; backend cannot reach the internet; both app containers run as non-root; data and uploads survive a full
  restart; backup + restore into a scratch DB reproduces the live row counts; a deliberately broken release rolls back
  automatically and the site keeps serving. ShellCheck and actionlint clean.
- CI workflows (root repo `.github/workflows/backend.yml`, frontend repo `.github/workflows/ci.yml`): build, tests, audit,
  image build + Trivy scan, secret scan. Linted with actionlint but not executed (no GitHub remote for the root repo yet).
- Scans run by hand: gitleaks on the committed files of both repos: no findings. Trivy on the images: see below.

Finding: the backend was on Spring Boot 3.4.2 (Jan 2025); its image had 55 fixable HIGH/CRITICAL library vulnerabilities
(Jackson, Tomcat, Spring...). Upgraded to 3.5.16 (125/125 tests pass, no code changes) and removed the stale
Testcontainers pin (1.20.4 breaks on newer Docker Engines).

Not done / needs a decision from the owner: domain and region (ICP filing if mainland China), image registry choice,
off-site backup destination, monitoring/alarms, real secret rotation, Aliyun OSS for uploads.
