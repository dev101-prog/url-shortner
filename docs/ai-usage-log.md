# AI usage log

## 2026-10-07 - design-implementer agent - build manifest steps 1-6 (design §7.5)

Branch: `main` (local commits only, not pushed; human instruction: one commit per step on main).
JDK 21.0.12 (Temurin), Maven 3.9.11 via wrapper, Docker 28.3.3, Testcontainers 1.19.8 (no extra socket config needed).

| Step | Commit | PRD IDs | Files drafted by AI | Verification run | Result |
|---|---|---|---|---|---|
| 1 Skeleton | 1275dc7 | NFR-5.2, NFR-5.3, OBS-1, NFR-4.4, AI-1 | pom.xml, mvnw/.mvn, docker-compose.yml (§7.2 verbatim), .env, .gitignore, application*.yml (§7.3 verbatim), logback-spring.xml, checkstyle.xml, UrlShortenerApplication, ApplicationStartupIT | `./mvnw -B verify` | PASS, 1 IT |
| 2 Schema + seed | 668f5da | FR-1.2, 2.3, 2.4, 4.1, 7.3, NFR-4.2, AI-1 | V1__init_schema.sql, V1_1__seed_demo_data.sql (§4.3/§4.4 verbatim, diff-checked), SeedDataIT | `./mvnw -B verify`; seed invariant 6,3,1,0,2 | PASS, 6 IT |
| 3 Domain/ports/config | 96cae93 | FR-4.1, 4.3, 1.4, 3.2, 7.5, NFR-4.3, NFR-5.1 | service.domain.*, service.port.*, AppProperties, ClockConfig, unit tests | `./mvnw -B verify` | PASS, 15 unit + 6 IT, service 100 % |
| 4 Validators | c229416 | FR-1.5, 2.1, 2.2, 4.2, 7.3, 7.4, NFR-4.7 | LinkValidator, UrlNormalizer, BotDetector, IpHasher, service.error.{ErrorCode,ApiException}, unit tests | `./mvnw -B verify` | PASS, 105 unit + 6 IT, the four classes 100 % lines |
| 5 Repositories | 26fa77e | FR-1.2, 1.4, 1.6, 2.3, 2.4, 6.1, 7.2, 7.6, NFR-4.2, 4.5 | LinkRepository, ApiKeyRepository, ClickEventRepository, SqlTypes, repository ITs | `./mvnw -B verify` | PASS, 105 unit + 17 IT |
| 6 Errors/filters/auth | 5d0301a | NFR-4.1, 4.2, 4.5, 4.6, OBS-1 | GlobalExceptionHandler, ErrorEnvelopeWriter, ErrorResponse, 4 filters, ApiKeyService, web slice tests, ApiKeyServiceTest, ApiKeyAuthIT | `./mvnw -B verify`; local run (compose postgres + `spring-boot:run -Dspring-boot.run.profiles=local`) with curl no/bad/revoked key | PASS, 152 unit/slice + 24 IT, overall 99.0 %, service.* 99.0 %; 3x 401 envelope, 0 ERROR logs |

Not run: OWASP Dependency-Check (configured in `-Psecurity`, CI only), gitleaks (not installed locally).

### Open questions for the reviewer
1. `ErrorCode`/`ApiException` live in `service.error`, not `api/error` (§7.1), because §8.4 rule 1 forbids service -> api while §6.2 has services throw `ApiException`.
2. New config keys with design-value defaults: `app.http.max-body-bytes` (8192), `app.cache.api-key-max-size` (10000).
3. 405/415 responses keep their HTTP status with envelope code `MALFORMED_REQUEST` (no §5.2 code exists).
4. `X-Request-Id` is echoed only if it matches `[A-Za-z0-9._-]{1,64}`, otherwise a UUID is generated.
5. Batch SQL in `ClickEventRepository` uses `JdbcOperations` (JdbcClient has no batch API).
6. Startup guard "fail if ip-salt is change-me outside local" (§13.3) is not implemented yet (no manifest step owns it).

## 2026-10-07 - design-implementer agent - batch B (steps 7-10), stopped in step 7

| Step | Commit | PRD IDs | Files drafted by AI | Verification run | Result |
|---|---|---|---|---|---|
| 7 LinkService/LinkController | none (uncommitted, BLOCKED) | FR-1.1-1.7, 2.1-2.4, 4.2, 4.3, 5.1, 5.2, 6.1, 6.2, NFR-4.3 | LinkService, LinkController, CreateLinkRequest, LinkResponse, LinkMetadataResponse; pulled forward from step 8: CacheConfig, LinkCacheExpiry, CaffeineLinkCache, CaffeineRateLimiter; RandomConfig; ApiKeyService now uses the apiKeys cache bean; GlobalExceptionHandler Retry-After + INVALID_EXPIRY mapping; tests LinkServiceTest, CaffeineRateLimiterTest, CaffeineLinkCacheTest, LinkApiIT (22), CreateRateLimitIT | `./mvnw -B verify` | Tests PASS (174 unit/slice, 47 IT; service.* 99.2 %, overall 99.2 %; Checkstyle 0) but G3 FAILS: 9x SpotBugs EI_EXPOSE_REP2 on constructor-injected Spring beans. A real CRLF_INJECTION_LOGS finding was fixed. |

Open question for the human: how to treat SpotBugs EI_EXPOSE_REP2 on dependency-injection constructors (exclusion filter vs code changes). Steps 8-10 not started.

### Batch B resumed after human decision on G3 (option 1, relayed by the coordinator)

| Step | Commit | PRD IDs | Files drafted by AI | Verification run | Result |
|---|---|---|---|---|---|
| 7 LinkService/LinkController | 71070dd | FR-1.1-1.7, 2.1-2.4, 4.2, 4.3, 5.1, 5.2, 6.1, 6.2, NFR-4.3, 4.5 | as listed above + `config/spotbugs/exclude.xml`, `docs/adr/0011-spotbugs-di-constructor-exclusion.md` | `./mvnw -B verify`; probe class confirmed the exclusion only hides constructor EI_EXPOSE_REP2 | PASS, 174 unit/slice + 47 IT, service.* 99.2 % |
| 8 Redirect | 9b22cd2 | FR-3.1-3.7, 4.3, 6.3, 7.1-7.4 | RedirectService, RedirectController, ClickBuffer (pulled forward), EffectiveStatus.evaluate, CachedLink.effectiveStatus, RedirectServiceTest, ClickBufferTest, RedirectApiIT, RedirectDegradationIT | `./mvnw -B verify` | PASS, 196 + 58 IT, service.* 99.4 % |
| 9 Click flush | 421600f | FR-7.1, 7.2, 7.4, 7.6, 5.3, OBS-3 | ClickFlushWorker, SchedulingConfig, ClickFlushWorkerTest, ClickPipelineIT, ClickShutdownIT | `./mvnw -B verify` | PASS, 204 + 61 IT, infra 100 % |
| 10 Stats | 3d50e46 | FR-7.5, 7.7, 5.2 | StatsRepository, StatsService, StatsResponse, stats endpoint, StatsServiceTest, StatsApiIT | `./mvnw -B verify`; local run with curl demo flow | PASS, 213 + 66 IT, service.* 99.4 %, overall 99.4 %; demo flow all expected codes, 0 ERROR/WARN logs |

Open questions for the reviewer: G3 approval was relayed by the coordinating agent (confirm at PR review); links-cache TTL rule for already-expired entries; new @DefaultValue keys app.rate-limit.max-buckets, app.analytics.flush-retry-backoff / shutdown-drain-timeout, app.stats.default-days / max-days; "max span 365 days" read as at most 365 days inclusive; test profile caps Hikari at 5 and the test container allows 300 connections.

## 2026-10-08 - design-implementer agent - batch C (steps 11-12)

Note: the coordinator rewrote the author of the step 1-10 commits to `sadeep`; SHAs in the tables
above predate that rewrite. Current SHAs: `git log`.

| Step | Commit | PRD IDs | Files drafted by AI | Verification run | Result |
|---|---|---|---|---|---|
| 11 Health/OpenAPI/ArchUnit | d6dcd1c | FR-8.1, 8.2, DOC-1, NFR-5.1, NFR-4.4, 4.6, OBS-1, TEST-1 | HealthController, ReadinessService, DatabaseHealthRepository, OpenApiConfig + annotations, openapi-baseline.json, OpenApiContractTest, LayeringArchTest, ClickPipelineConfig (infra gets plain values), IpSaltGuard, AccessLogFilter, EnvelopeErrorController, tests | `./mvnw -B verify` | PASS, 242 unit/slice/contract/arch + 70 IT, service.* 99.5 %, overall 99.5 % |
| 12 Packaging/scripts/docs | (this commit) | TEST-2, DOC-2, NFR-4.2, NFR-6.2 | Dockerfile (verbatim §13.2) + .dockerignore, scripts/demo.sh (re-runnable, PASS/FAIL), scripts/create-api-key.sh, perf/k6/*.js, docs/perf/*, runbook, scaling, ADR 0001-0009 (+0010 placeholder) | `docker compose up --build -d`, demo.sh (16/16 PASS, twice), create-api-key.sh, k6 hit + miss, DB stop/start | demo PASS; k6 FAILS NFR-1 on the laptop: hit p95 39.64 ms (< 20), miss p95 77.82 ms (< 50), 0 errors |

Open questions for the reviewer: NFR-1 not met on the developer laptop (see docs/perf/README.md);
perf run needs rate-limit/negative-cache overrides on a separate container.

### Batch C extra deliverables (2026-10-08)
| Deliverable | Commit | Verification | Result |
|---|---|---|---|
| a) Postman collection + environment | 995d934 | newman 6 (Docker), two runs | 23 requests, 49 assertions, 0 failures |
| b) GitHub Actions, PR template, CODEOWNERS, .gitleaks.toml, .editorconfig | 92c3265 | PyYAML parse; gitleaks v8.21.2 locally | YAML OK; no leaks |
| c) .gitlab-ci.yml | e4eabb6 | PyYAML + Ruby Psych parse | OK (not executed on GitLab) |
| d) README.md | e695231 | quick start from a fresh clone + fresh DB volume | demo 16/16 PASS |
| e) docs/quality-report.md | (this commit) | numbers from the latest verify, k6, newman, demo and gitleaks runs | see report |

## 2026-10-08 - fixes on main after batch C

| Change | PRD IDs | Files | Verification | Result |
|---|---|---|---|---|
| /readyz 503 uses the standard envelope (`NOT_READY`, details.checks.db=DOWN); human decision reconciling §5.2/§5.3; OpenAPI baseline change approved | FR-8.2, NFR-4.6 | HealthController, HealthIT, openapi-baseline.json (only /readyz changed), runbook, ADR 0009, quality report | `./mvnw -B verify` | PASS, 242 + 70 IT |
| Portability: no bind mounts or host networking in documented tooling (`newman`/`k6` compose services under profile `test`, `postman/Dockerfile`, `perf/compose.perf.yml`, `scripts/postman.sh`, `scripts/k6.sh`, explicit app healthcheck); human decision | DOC-2, TEST-2 | docker-compose.yml, postman/Dockerfile, perf/compose.perf.yml, scripts, README, docs/perf/README.md, quality report | from the ~/Desktop checkout: compose up --build, `docker compose run --rm newman`, scripts/postman.sh, demo.sh, k6 smoke (hit and miss, 5 VUs, 15 s) | newman 49/49 twice, demo 16/16, k6 smoke exit 0 with 0 % errors |

## 2026-10-08 - Phase 2 scenario B2 (branch demo/b2-seeded-bug)
| Scenario | PRD IDs | Files drafted by AI | Verification | Result | Sign-off |
|---|---|---|---|---|---|
| B2 seeded defect + regression tests | SCN-B2, FR-3.2, FR-4.3 | scripts/demo/b2-seeded-bug.patch (applied), RedirectServiceTest.b2_expiredFromCacheReturnsGone, B2ExpiredFromCacheIT, docs/scenarios/B2.md | `./mvnw -B clean verify` | FAILS as intended: unit 243/2 failed; with failure.ignore, IT 71/2 failed (all four failures are expired-from-cache) | standard review |

## 2026-10-08 - Phase 2 scenario B2 fix (branch fix/b2-expired-cache-410)
| Scenario | PRD IDs | Files | Verification | Result | Sign-off |
|---|---|---|---|---|---|
| B2 fix: single status evaluator | SCN-B2, FR-3.2, FR-4.3 | RedirectService, CachedLink (seeded patch reversed), docs/scenarios/B2.md | `./mvnw -B clean verify` | PASS, 243 + 71 IT, regression tests green; root cause: duplicated status logic in two branches | standard review |
