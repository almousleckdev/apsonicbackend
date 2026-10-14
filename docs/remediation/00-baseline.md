# Baseline (2026-10-14, before any remediation)

Environment: macOS, JDK 21.0.11 (Homebrew), Maven wrapper 3.9.16, Node/npm local, Docker NOT running.
Backend commit: `082c5b1` (root repo). Frontend: branch `remediation` @ `6d3c40c` (apsonicui repo; its `main` is untouched).

## Backend: `./mvnw -B clean test`
17 tests: 10 pass, 7 error, 0 failures.

| Test | Result | Cause |
|---|---|---|
| UserServiceTest, AuthServiceTest, NewsServiceTest | pass | |
| BackendApplicationTests, AuthIntegrationTest | error | Testcontainers: "Could not find a valid Docker environment" (environment, not code) |
| ContextLoadTest | error | `UserRepository` bean missing: `application-test.yml` excludes JPA, but the full app context needs it (test rot) |
| AuthControllerTest (4 tests) | error | `@WebMvcTest` context lacks `TokenBlacklistService` bean, added to AuthController after the test was written (test rot) |

Note: running `mvnw test` WITHOUT `clean` first failed all 17 with
`ClassFormatError: Invalid method Code length 0 in NewsSectionDto`. That was a stale/corrupt `target/`,
not a Mockito/JDK incompatibility (the initial audit diagnosis was wrong). Always run `clean`.

## Frontend (apsonicui)
- `npx tsc --noEmit`: 5 errors (`titleEn` missing on `CmsPageDto`; `"TEXT_IMAGE"` not in `SectionType`).
- `npm run build`: FAILS at type-check on the above (production build is currently impossible).
- `npm run lint`: 157 problems (90 errors, 67 warnings).
- `npm audit --omit=dev`: 37 vulns (1 critical, 6 high, 30 moderate).
- No frontend tests exist.
