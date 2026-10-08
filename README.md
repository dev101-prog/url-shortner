# URL Shortener

A production-shaped URL shortener built from a PRD and a detailed design through an AI-assisted,
human-approved workflow: a versioned REST API to shorten, resolve, inspect and deactivate links,
with click analytics kept off the redirect hot path.

- PRD: [docs/prd-url-shortener.md](docs/prd-url-shortener.md)
- Detailed design (source of truth): [docs/design-url-shortener.md](docs/design-url-shortener.md)
- Quality report with measured numbers: [docs/quality-report.md](docs/quality-report.md)

## Quick start (only Docker needed)

```bash
# 1. Clone
git clone https://github.com/dev101-prog/url-shortner.git && cd url-shortner

# 2. Build and start Postgres + the app (the committed .env enables the compose "full" profile)
docker compose up --build

# 3. In another terminal: run the end-to-end demo (prints PASS/FAIL per step, exit code 0 = all passed)
bash scripts/demo.sh
```

Then explore:
- Swagger UI: <http://localhost:8080/docs> (OpenAPI JSON at <http://localhost:8080/openapi.json>)
- Postman: import [postman/url-shortener.postman_collection.json](postman/url-shortener.postman_collection.json)
  and [postman/local.postman_environment.json](postman/local.postman_environment.json), or run it headless
  on the compose network with `docker compose run --rm --build newman` (or `bash scripts/postman.sh`)
- Local demo API keys (local only, design §4.4): `demo-key-alice-0001`, `demo-key-bob-0002`,
  `demo-key-revoked-0003` (revoked).

Developer loop (JDK 21): `docker compose up -d postgres`, then
`./mvnw spring-boot:run -Dspring-boot.run.profiles=local`; build and all gates with `./mvnw -B verify`.

## Architecture
One Spring Boot 3.3 service (Java 21, Web MVC on virtual threads) with strict layers
(api → service → repository, infra behind service ports) and one PostgreSQL 16 database. Redirects
read an in-process Caffeine cache, then Postgres; clicks go to a bounded in-memory buffer that a
scheduled worker flushes in batches. See design [§1](docs/design-url-shortener.md#1-plan-rationale-and-design-principles),
[§2](docs/design-url-shortener.md#2-system-architecture-block-diagram),
[§3](docs/design-url-shortener.md#3-component-design) and the sequence flows in
[§6](docs/design-url-shortener.md#6-core-sequence-flows-all-flows). Decisions: [docs/adr/](docs/adr/).

## API overview
| Method | Path | Auth | Success | Errors |
|---|---|---|---|---|
| POST | `/api/v1/links` | X-API-Key | 201 (200 on dedupe hit) | 400, 401, 409, 411, 413, 422, 429, 503 |
| GET | `/api/v1/links/{code}` | X-API-Key (owner) | 200 | 401, 404 |
| DELETE | `/api/v1/links/{code}` | X-API-Key (owner) | 204 | 401, 404 |
| GET | `/api/v1/links/{code}/stats` | X-API-Key (owner) | 200 | 401, 404, 422 |
| GET | `/{code}` | public | 302 | 404, 410, 429 |
| GET | `/healthz`, `/readyz` | public | 200 | 503 (`/readyz`) |

Every error uses one envelope, `{"error":{"code","message","details"}}`. Contract and error codes:
design [§5](docs/design-url-shortener.md#5-api-contract).

## Database schema and seed data
Four tables (`owners`, `api_keys`, `links`, `click_events`), Flyway migrations in
`src/main/resources/db/migration`, demo data in `db/seed` (loaded only with the `local` profile).
See design [§4](docs/design-url-shortener.md#4-postgresql-data-model-creation-script-and-sample-data).
Provision a real key: `bash scripts/create-api-key.sh <owner> [label]` (prints it once).

## Testing strategy
Test pyramid (design [§8](docs/design-url-shortener.md#8-testing-strategy)): unit tests with a fixed
`Clock`, `@WebMvcTest` slices for filters and error mapping, Testcontainers integration tests
(`*IT`, `postgres:16.4-alpine`), ArchUnit layering rules, an OpenAPI contract baseline, failure
injection, and k6 load tests.

```bash
./mvnw -B verify                                   # everything (Docker must be running)
./mvnw -B test                                     # unit, slice, contract, architecture
bash scripts/demo.sh                               # end-to-end against a running stack
```
Load tests: [perf/k6/](perf/k6/), e.g. `bash scripts/k6.sh perf/k6/redirect-hit.js` (k6 runs on the compose
network and reads the script from stdin; nothing is mounted). Results and the perf setup:
[docs/perf/README.md](docs/perf/README.md).

## Quality gates and measurement of quality
| Gate | Check | Where |
|---|---|---|
| G1 | Spotless (google-java-format), Checkstyle | `./mvnw verify` |
| G2 | `javac -Xlint:all -Werror` | `./mvnw verify` |
| G3 | SpotBugs + FindSecBugs, 0 High/Medium ([ADR 0011](docs/adr/0011-spotbugs-di-constructor-exclusion.md)) | `./mvnw verify` |
| G4 | Surefire + Failsafe, 100 % pass | `./mvnw verify` |
| G5 | JaCoCo: service ≥ 85 % lines, overall ≥ 75 % | `./mvnw verify` |
| G6 | ArchUnit layering rules | `./mvnw verify` |
| G7 | OpenAPI contract vs `openapi-baseline.json` | `./mvnw verify` |
| G8 | gitleaks | CI |
| G9 | OWASP Dependency-Check, fail on CVSS ≥ 7 (`-Psecurity`) | CI |
| G10 | Requirement traceability script | not implemented yet |
| G11 | k6 performance thresholds | release gate, [docs/perf](docs/perf/README.md) |
| G12 | Human review (branch protection, CODEOWNERS) | GitHub / GitLab settings |

Definitions and KPIs: design [§9](docs/design-url-shortener.md#9-quality-gates-and-measurement-of-quality).
Measured results: [docs/quality-report.md](docs/quality-report.md).

## AI-assisted engineering model
- **The engineer leads and approves; AI assists within tasks.** AI drafts code, tests and docs; the
  engineer reads every line, runs it and owns correctness, maintainability and production readiness.
- **No autonomous actions.** AI never merges, pushes, runs shared migrations or deploys.
- **Secure AI usage.** No real secrets or production data in prompts, only the demo keys and
  RFC 5737 IPs; dependencies proposed by AI are checked by a human.
- **Human sign-off for high-impact changes:** migrations, security-sensitive code, the API
  contract baseline, dependencies and config defaults (CODEOWNERS, PR template).
- Provenance: every AI-assisted change is logged in [docs/ai-usage-log.md](docs/ai-usage-log.md).

Details: design [§10](docs/design-url-shortener.md#10-ai-assisted-engineering-model).

## Scenarios
- **Greenfield (G1):** the MVP on `main` (build manifest steps 1–12).
- **Brownfield and ambiguous:** delivered on branches, each with its own evidence trail:
  `feat/b1-owner-dedupe-default` (B1 enhancement + migration), `demo/b2-seeded-bug` (B2 seeded
  defect), `fix/b2-expired-cache-410` (B2 fix), `refactor/b3-code-generator` (B3),
  `chore/b4-tests-docs` (B4), `feat/a-analytics-accuracy` (A1–A3).

See design [§11](docs/design-url-shortener.md#11-scenarios-greenfield-brownfield-ambiguous).

## Risks, trade-offs and guardrails
Main trade-offs: a local cache instead of Redis (stale cache across instances for up to 10 min),
an in-memory click buffer (≤ 1 s of clicks lost on crash), fixed-window per-instance rate limits,
and no DB password for local runs only. Operational behaviour per failure mode is in the
[runbook](docs/runbook.md); the scale path is in [docs/scaling.md](docs/scaling.md). Full matrix:
design [§12](docs/design-url-shortener.md#12-risk-security-and-operational-mitigations).

## Timeline
Six weeks from 2026-10-12 (M0 setup to M6 sign-off): design
[§14](docs/design-url-shortener.md#14-product-timeline).

## Assumptions and limitations
Local Postgres runs with `trust` auth (development only), there is no TLS, cache and rate limits
are per instance, GeoIP is not implemented (`country` is null for new clicks), and NFR-1 latency
targets were not met on the developer laptop ([docs/perf](docs/perf/README.md)). Full list and PRD
deviations: design [§16](docs/design-url-shortener.md#16-assumptions-limitations-and-deviations-from-the-prd).

## CI/CD
- **GitHub Actions** ([.github/workflows/ci.yml](.github/workflows/ci.yml)): `./mvnw -B verify`,
  gitleaks, OWASP Dependency-Check (`NVD_API_KEY` secret optional).
- **GitLab** ([.gitlab-ci.yml](.gitlab-ci.yml)): lint → test → security → package → manual
  deploys (production only on tags through a protected environment). Required variables are listed
  at the top of the file.

## Troubleshooting
| Problem | Fix |
|---|---|
| Port 5432 already in use | stop the local Postgres (`brew services stop postgresql`, or the other container), or change the host port in `docker-compose.yml` |
| Port 8080 already in use | stop the process using it (`lsof -i :8080`), or change the app's host port mapping |
| Want a fresh database / re-seed | `docker compose down -v` (deletes the volume), then `docker compose up --build` |
| `demo.sh` reports FAIL on `readyz` | wait until `curl localhost:8080/readyz` returns UP, then re-run |
| App exits with "app.analytics.ip-salt ... must be set" | outside the `local` profile set `APP_IP_SALT` to a secret value |
| Tests fail to start containers | Docker must be running; Testcontainers needs access to the Docker socket |
| `newman` / `k6` service "depends on undefined service app" | keep the committed `.env` (it enables the `full` profile); don't pass only `--profile test` |
