# Quality report (design §9.3)

Measured on 2026-10-08 on a developer laptop (Intel i7-9750H, 12 threads, 32 GB; JDK 21.0.12;
Docker Desktop 28.3.3). Code state: step 12 (`85e8440`) plus the docs/CI commits after it. Every
number below comes from a run made for this report; nothing is estimated.

## Tests (`./mvnw -B verify`, all passing, 0 skipped)

| Type | Location | Classes | Tests |
|---|---|---|---|
| Unit (services, infra, config, domain; fixed `Clock`) | `unit/` | 19 | 178 |
| Web slice (`@WebMvcTest`: filters, error mapping, access log, `/error`) | `web/` | 7 | 56 |
| Architecture (ArchUnit, design §8.4 rules 1-5) | `arch/` | 1 | 6 |
| API contract (generated OpenAPI vs baseline) | `contract/` | 1 | 2 |
| Integration (Testcontainers `postgres:16.4-alpine`, Failsafe) | `it/` | 14 | 70 |
| **Total** | | **42** | **312** |

The integration tests include failure injection (a links cache that throws on every call, a full
click buffer, a stopped database for `/readyz`), a 20-way concurrent alias race, graceful-shutdown
draining, and seed-data stats.

## Coverage (JaCoCo, unit + integration merged)

| Scope | Lines | Branches | Gate |
|---|---|---|---|
| `service` packages (aggregate) | **99.5 %** (363/365) | 97.2 % (137/141) | ≥ 85 % per package: met (lowest: `service` 99.3 %) |
| Overall | **99.5 %** (975/980) | 95.7 % (270/282) | ≥ 75 %: met |

## Gates (design §9.2)

| Gate | Check | Result |
|---|---|---|
| G1 | Spotless (google-java-format 1.28.0), Checkstyle 10.26.1 | PASS: 0 violations |
| G2 | `javac -Xlint:all -Werror` (main and test) | PASS: 0 warnings |
| G3 | SpotBugs 4.9.8 + FindSecBugs 1.14.0, threshold Medium | PASS: 0 findings (narrow DI-constructor EI_EXPOSE_REP2 exclusion, ADR 0011) |
| G4 | Surefire + Failsafe | PASS: 312 tests, 0 failures, 0 skipped |
| G5 | JaCoCo | PASS: see coverage above |
| G6 | ArchUnit | PASS: 6 rules (rule 2 is checked as two parts) |
| G7 | OpenAPI contract baseline | PASS: generated spec equals `openapi-baseline.json` |
| G8 | gitleaks (zricethezav/gitleaks:v8.21.2, `.gitleaks.toml`) | PASS locally: 16 commits plus the working tree, no leaks. Also in both CI pipelines |
| G9 | OWASP Dependency-Check (CVSS ≥ 7 fails) | **CI only, not run locally** (`-Psecurity`; needs the NVD feed) |
| G10 | Traceability script (`check-traceability.sh`) | **Not implemented yet.** Test names do carry PRD IDs (e.g. `fr3_2_unknown404_inactive404_expired410`) |
| G11 | k6 performance thresholds (release gate) | **FAIL on this laptop**, see below |
| G12 | Human review (branch protection, CODEOWNERS) | Configured in-repo (CODEOWNERS, PR template); branch protection is a repository setting to enable on GitHub/GitLab |

## Performance (k6 0.54.0, design §8.6 profile: 2 min ramp to 200 VUs + 5 min steady)

| Scenario | Requests | Throughput | p50 | p95 | p99 | Errors | Target | Result |
|---|---|---|---|---|---|---|---|---|
| Cache hit | 4,275,201 | 10,084 req/s | 13.26 ms | **39.64 ms** | 71.12 ms | 0 % | p95 < 20 ms | FAIL |
| Cache miss (negative cache off) | 2,142,574 | 5,101 req/s | 28.43 ms | **77.82 ms** | 119.52 ms | 0 % | p95 < 50 ms | FAIL |

App, Postgres and k6 shared one Docker Desktop VM, with closed-loop VUs and no think time. The
perf container used documented rate-limit and negative-cache overrides. Raw summaries and the
setup are in [docs/perf/](perf/README.md). NFR-1 is not demonstrated in this environment.
Thresholds and code were not tuned to pass.

## Functional end-to-end checks

| Check | Result |
|---|---|
| `scripts/demo.sh` against `docker compose up --build -d` (CUJ-1 to CUJ-4) | 16/16 PASS, re-run twice on the same database; exit 1 with 0/16 when the app is unreachable |
| Quick start from a fresh clone and a fresh DB volume (README steps 1-3) | build OK, `/readyz` UP, demo 16/16 PASS |
| Postman collection via `postman/newman:6-alpine` | 23 requests, 49 assertions, 0 failures, two consecutive runs |
| Postgres stopped under the running app | `/readyz` 503, `/healthz` 200, API and cache-miss redirects 500; after restart `/readyz` 200 within about 2 s, create 201, no app restart |
| Local runs (`local` profile) | 0 ERROR logs, no raw API keys or client IPs in the logs |

## Design deviations and decisions accepted so far

| # | Decision | Status |
|---|---|---|
| 1 | `ErrorCode`/`ApiException` live in `service.error`, not `api/error` (design §6.2 vs §8.4 rule 1) | accepted provisionally by the coordinator; human confirmation pending |
| 2 | Config keys not in §7.3, with defaults equal to the design values via `@DefaultValue`: `app.http.max-body-bytes`, `app.cache.api-key-max-size`, `app.rate-limit.max-buckets`, `app.analytics.flush-retry-backoff`, `app.analytics.shutdown-drain-timeout`, `app.stats.default-days`, `app.stats.max-days`, `app.health.db-timeout` | accepted (keep) |
| 3 | 405/415 keep their status with envelope code `MALFORMED_REQUEST` (no §5.2 code exists) | accepted (keep) |
| 4 | `X-Request-Id` echoed only if it matches `[A-Za-z0-9._-]{1,64}` | accepted (keep) |
| 5 | API-key filter matches `/api/v1/**` on decoded path segments (no `;param` or `%`-encoding bypass) | accepted (keep) |
| 6 | SpotBugs: narrow EI_EXPOSE_REP2 exclusion for DI constructors only (ADR 0011) | accepted (human decision) |
| 7 | Infra built ahead of its manifest step (cache and rate limiter in step 7, click buffer in step 8) | accepted |
| 8 | Links-cache TTL: `min(10 min, expiresAt - now)` only while the expiry is in the future | accepted |
| 9 | Rate limiter fails open with a metric if its counter store errors | accepted |
| 10 | `RandomConfig` provides the `SecureRandom` bean | accepted |
| 11 | ArchUnit rule 4 kept exactly as worded; infra receives plain values from `config.ClickPipelineConfig` | accepted (human decision) |
| 12 | `/readyz` 503 uses the standard envelope: `NOT_READY` (§5.2) with `details.checks.db = "DOWN"`; the 200 body stays `{"status":"UP","checks":{"db":"UP"}}` (reconciles §5.2 with §5.3) | human decision (2026-10-08); OpenAPI baseline change approved |
| 13 | Readiness check goes api → `ReadinessService` → `DatabaseHealthRepository` (rule 3: api never touches repositories) | implementation decision, for review |
| 14 | Stats "max span 365 days" means at most 365 days inclusive; range validated before ownership (flow §6.7) | implementation decision, for review |
| 15 | `/error` is handled by `EnvelopeErrorController`, one read-only handler per HTTP method (FindSecBugs CSRF rule) | implementation decision, for review |
| 16 | `scripts/demo.sh` uses a unique alias per run and an expiry relative to today (re-runnable) | requested by the coordinator |
| 17 | Test-only infrastructure: Hikari pool capped at 5 in the test profile; the test container allows 300 connections | test configuration only |

## Open items
- NFR-1 latency targets not met on the laptop. Re-run on the reference environment, agree on think
  time, and profile the hot path (G11).
- G10 traceability script not yet written.
- G9 has not been run yet; the first CI run will produce the Dependency-Check report.
