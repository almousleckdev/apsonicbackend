# Remediation progress

| ID | Finding | Status | Commit |
|---|---|---|---|
| C1 | Unauthenticated debug endpoint | Fixed + regression test | 94d4348 |
| C2 | Default Super Admin (Flyway V1 seed + reset on every boot) | Fixed (code). Seed row is disabled at startup, not deleted | f4d7cff |
| C3 | Committed secret defaults / no JWT key validation | Fixed (code). **Rotation of existing secrets is the operator's task, not done** | a112620 |
| C4 | Next.js critical advisories | Fixed: `npm audit --omit=dev` = 0. 5 dev-only highs (eslint tooling) accepted | apsonicui ceb8c12 |
| H1 | Stored XSS via SVG upload | Fixed: content sniffing, SVG removed, `/uploads` nosniff + sandbox CSP | see git log |
| -  | Frontend production build broken | Fixed (5 type errors) | apsonicui af0c89b |

Open: H2/H4 (in-memory access token, short expiry, jti/iss/aud), CSRF review of cookie endpoints,
H5 (rate limiting), H6 (actuator/swagger/error detail by profile), M3 (security headers), H3 (HTML sanitising).

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
