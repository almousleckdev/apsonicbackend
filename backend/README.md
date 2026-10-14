# Apsonic Backend

Spring Boot 3.4 / Java 21 REST API for the Apsonic Africa website and its admin portal (products, categories, news,
CMS pages, homepage config, contact messages, users). A modular monolith: one deployable, feature modules with
clear boundaries.

## Modules

```
com.apsonic.backend
├── config/        Spring configuration (security, CORS, JPA, cache, OpenAPI) and typed properties
├── security/      JWT provider/filter, token blacklist, CSRF guard for cookie endpoints, user principal
├── shared/        API envelopes, exceptions, rate limiter, HTML sanitizer, upload validation, audit base classes
└── modules/
    ├── auth/      login, refresh/logout, profile, users, roles, first-admin bootstrap
    ├── product/   products, specs, colours, reviews           ├── category/  categories
    ├── news/      articles (rich text + sections)             ├── contact/   contact form + admin inbox
    ├── page/      CMS pages, sections, revisions, navigation  ├── cms/       key/value config (homepage)
    └── trash/     soft-deleted items: list, restore, purge
```

Each module follows `controller → service → repository` with DTOs and MapStruct mappers; entities never leave
the service layer. Schema changes go through Flyway (`src/main/resources/db/migration`).

## Requirements

Java 21, Docker (PostgreSQL 16, Redis 7; also required by the integration tests). Maven wrapper included.

## Run locally

```bash
cp .env.example .env
# Generate real secrets - there are NO defaults and the app refuses to start with weak/known ones:
openssl rand -base64 48     # -> JWT_SECRET
openssl rand -base64 24     # -> DB_PASSWORD, REDIS_PASSWORD, PGADMIN_PASSWORD
# Set BOOTSTRAP_ADMIN_USERNAME / _EMAIL / _PASSWORD (>= 12 chars, upper+lower+digit+symbol) for the first admin.

docker compose up -d         # postgres, redis, pgadmin (bound to 127.0.0.1)
./mvnw spring-boot:run       # SPRING_PROFILES_ACTIVE=local comes from .env
```

There is no default account. The first Super Admin is created from `BOOTSTRAP_ADMIN_*` only when no active Super
Admin exists; remove `BOOTSTRAP_ADMIN_PASSWORD` afterwards. Accounts that still use a publicly known default
password (e.g. the one older Flyway seeds created) are disabled at startup.

## Profiles

| Profile | Use | Differences |
|---|---|---|
| `prod` (**default**) | deployments | health probe only; no Swagger; no error detail; strict Flyway |
| `local` | development | Swagger UI at `/swagger-ui.html`, richer actuator, verbose errors, lenient Flyway |

Set `SPRING_PROFILES_ACTIVE=local` for development (already in `.env.example`).

## Security model (summary)

- **Auth**: 15-minute access JWT (`jti`, `iss`, `aud`; revocable) sent as a Bearer token and kept in browser memory only;
  a rotating 7-day refresh token in an `HttpOnly`, `SameSite=Strict` cookie (only its SHA-256 is stored; reuse revokes all
  sessions). Disabled/locked users lose access immediately.
- **CSRF**: `/auth/refresh` and `/auth/logout` require `X-Requested-With: apsonic-web` and an allow-listed `Origin`.
- **Rate limits** (per client IP, Redis): login 10/min, refresh 60/min, contact 5/10 min, news view counter 20/h.
  Client IPs come from `X-Forwarded-For` only when the peer is a private-range proxy: **never expose this service
  directly to the internet**.
- **Uploads**: real file type is detected from content (JPG, PNG, GIF, WEBP, MP4, WEBM, PDF); SVG/HTML are rejected;
  `/uploads` is served `nosniff` with a sandbox CSP.
- **CMS HTML** is sanitized with an allow-list on save and on read (`shared/html/HtmlSanitizer`).
- Role rules: `@PreAuthorize` on every admin controller (ADMIN, SUPER_ADMIN; EDITOR for content endpoints).

## Tests

```bash
./mvnw clean test      # always `clean`: a stale target/ once produced misleading class-format errors
```

Unit tests mock collaborators; integration tests (`*IntegrationTest`) start real PostgreSQL and Redis with Testcontainers,
so Docker must be running. They cover authentication/session lifecycle, CSRF, rate limiting, upload validation, HTML
sanitization, profile hardening and Flyway strictness.

## Operations notes

- Connection pool and threads are conservative defaults (`DB_POOL_MAX=20`, `SERVER_THREADS_MAX=200`); each replica adds
  `DB_POOL_MAX` connections to the database limit.
- Changing `JWT_SECRET` signs everyone out. `JWT_ACCESS_EXPIRATION_MS` above one hour fails startup by design.
- Uploads are written to `./uploads` on local disk for now (not suitable for multiple instances); object storage
  (Aliyun OSS) is the planned target.
- Remediation history, accepted risks and the deployment checklist: `../docs/remediation/`.
