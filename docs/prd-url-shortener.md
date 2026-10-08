# Product Requirements Document (PRD): AI-Assisted URL Shortener

| Field | Value |
|---|---|
| Document status | Draft v1.0 — input to Detailed Design phase |
| Product | URL Shortener (reference system for AI-Assisted Software Engineering) |
| Date | 2026-10-07 |
| Source inputs | Vision statement, Goals / Non-goals, FR-1…FR-8, NFR-1…NFR-6, Ambiguity-resolution log |
| Next phase | Detailed Design (architecture, data model, API contract, sequence diagrams) |

---

## 1. Executive Summary & North Star

### 1.1 Vision restatement
Build a production-shaped **URL shortener** that exposes a **versioned REST API** to shorten, resolve (redirect), inspect and deactivate links, captures **click analytics off the redirect hot path**, and serves as the vehicle for demonstrating an **AI-assisted software engineering workflow** across three scenario types: **greenfield**, **brownfield** and **ambiguous** requirements.

### 1.2 North Star
> *A redirect that is always fast and always available, built through a workflow where AI accelerates every engineering step but a human approves every change that reaches the main branch, the database or an environment.*

North Star metric: **p95 redirect latency < 20 ms (cache hit) with 100% redirect availability while dependent subsystems (Redis, analytics) are degraded.**

### 1.3 Core value proposition
- **For API consumers:** short, durable links with custom aliases, expiry and per-link analytics, with no account sign-up beyond an API key.
- **For the engineering organization:** a small but non-trivial system (hot path + async pipeline + cache + auth + data durability) that is realistic enough to evaluate how AI assistance performs on new builds, on changes to existing code, and on under-specified requests — while keeping humans in control of merge, migrate and deploy.

### 1.4 Why now
The project is a reference implementation: its requirements, design artefacts, code, tests and docs together form an evidence trail showing **how requirements flow from vision → PRD → design → tasks → code**, and where human judgement is applied.

---

## 2. Target Audience & Personas

| Persona | Description | Primary needs |
|---|---|---|
| **P1 — API Integrator (Link Owner)** | Developer or service holding an API key; creates and manages links programmatically. | Fast, predictable create API; custom aliases; expiry; deactivate; per-link stats. |
| **P2 — End Visitor** | Anonymous person who clicks a short link (browser, app, bot). | Instant redirect; clear error when link is gone (404 / 410). |
| **P3 — Operator / SRE** | Runs the service locally or in a single-region deployment. | Health/readiness probes, graceful degradation, logs/metrics, documented failure modes. |
| **P4 — Engineer using AI assistance** | Builds and evolves the system with an AI assistant. | Clear, unambiguous tickets; traceable requirement IDs; tests that prove acceptance criteria; guardrails on AI actions. |
| **P5 — Reviewer / Tech Lead** | Approves PRs, migrations and deployments. | Small reviewable diffs, ADRs for decisions, coverage reports, explicit human gates. |

### 2.1 User pain points addressed
1. Long URLs are hard to share, track and retire.
2. Redirect services that synchronously write analytics become slow or fail when the analytics store is unhealthy.
3. Permanent (301) redirects are cached by browsers, so clicks are lost and deactivation does not take effect.
4. Storing raw visitor IPs creates unnecessary PII liability.
5. AI-generated changes are hard to trust without traceability to requirements and without human approval gates.

---

## 3. Scope: In-Scope vs. Anti-Goals

### 3.1 In-scope (MVP)
| Area | Capability |
|---|---|
| Link lifecycle | Create (random 7-char base62 code or custom alias), resolve/redirect, read metadata, soft-delete (deactivate), optional expiry. |
| Analytics | Asynchronous click capture; totals, clicks per day, top referrers, top countries; bot flagging. |
| Security | API-key auth on writes and owner reads, input validation, URL scheme allowlist, rate limiting, no secrets in repo. |
| Operability | `/healthz`, `/readyz`, structured logs, service metrics, graceful degradation when Redis or analytics are down. |
| Developer experience | OpenAPI/Swagger page, versioned API (`/api/v1`), layered codebase, type checks, ≥85% service-layer coverage, local load test. |
| AI-assisted engineering | Three demonstrated scenarios (greenfield, brownfield, ambiguous) with traceable artefacts and human approval gates. |

### 3.2 Anti-goals (explicitly excluded, with rationale)
| Anti-goal | Rationale |
|---|---|
| No UI beyond the OpenAPI/Swagger page | The product surface is the API; UI work would dilute engineering focus. |
| No multi-region deployment | Single-region is sufficient to show a stateless app tier and a documented scale-out path (NFR-6). |
| No user sign-up flow or billing | API keys provide traceable ownership without building account management. |
| No autonomous agent pipeline | AI **never** merges, runs migrations or deploys on its own; every such action requires an explicit human approval. |
| No 301 permanent redirects by default | Breaks analytics and deactivation (see §7.1). |
| No raw IP storage | PII minimisation; salted hash + coarse country only. |
| No real-time (sub-second) analytics | Near-real-time (seconds) allows batching off the hot path. |
| No link editing (changing target of an existing code) | Not in requirements; avoids abuse via bait-and-switch. Candidate for a future phase. |

---

## 4. Critical User Journeys

### CUJ-1 — Create and share a link (P1)
1. Integrator sends `POST /api/v1/links` with `X-API-Key`, `{ "url": "...", "alias"?: "...", "expires_at"?: "...", "dedupe"?: bool }`.
2. Service authenticates the key, checks the rate limit, validates URL (scheme allowlist, length, not self-referential), alias format/reserved words, and expiry window.
3. If `dedupe` applies and an active link from the same owner already points to the same normalised URL, return that link (`200`). Otherwise generate a code (or use alias), persist durably, warm the cache.
4. Return `201` with `{ code, short_url, target, created_at, expires_at, status }`.
5. **Success:** the short URL redirects immediately.

### CUJ-2 — Visitor clicks a short link (P2)
1. Visitor requests `GET /{code}`.
2. Service looks up `code` in Redis; on miss (or Redis unavailable) reads the database and back-fills the cache when possible.
3. Unknown or inactive → `404`; expired → `410`; otherwise → `302 Location: <target>` with `Cache-Control: private, no-store` (or equivalent) so browsers do not cache the redirect.
4. A click event (timestamp, code, referrer, user-agent, bot flag, salted IP hash, coarse country) is enqueued to an in-memory buffer **without awaiting persistence**.
5. A background worker flushes events in batches to the analytics store.
6. **Success:** redirect completes within NFR-1 targets regardless of analytics/Redis health.

### CUJ-3 — Inspect, analyse and retire a link (P1)
1. Integrator calls `GET /api/v1/links/{code}` → target, created, expiry, status, total clicks.
2. Integrator calls `GET /api/v1/links/{code}/stats` → totals, clicks/day, top referrers, top countries (when available), bot vs human split.
3. Integrator calls `DELETE /api/v1/links/{code}` → `204`; link is soft-deleted, cache entry invalidated; subsequent redirects return `404`.
4. **Success:** owner sees accurate near-real-time stats; deactivated link stops redirecting within one cache-invalidation cycle (target: immediately).

### CUJ-4 — Operator handles a degraded dependency (P3)
1. Redis becomes unreachable.
2. `/healthz` stays `200` (process alive); `/readyz` reports Redis failure per readiness policy (see §7.2 OQ-6).
3. Redirects fall back to the database (latency within the miss budget); rate limiting falls back to an in-process limiter.
4. When Redis recovers, caching resumes automatically with no restart.

### CUJ-5 — Engineer delivers a change with AI assistance (P4/P5)
1. Engineer picks a ticket linked to a requirement ID (e.g. `URL-FR-3.2`).
2. AI assistant proposes design notes, code, tests and docs on a feature branch.
3. CI runs lint, type-check, tests, coverage gate and contract checks.
4. Human reviewer approves the PR; human applies migrations and triggers deployment.
5. **Success:** every merged change traces to a requirement and passed all gates; AI performed no merge/migrate/deploy action.

---

## 5. Functional Requirements & Acceptance Criteria

**Conventions**
- IDs are stable and map 1:1 to JIRA tickets (Epic = group, Story = requirement). The source requirement ID (FR-x / NFR-x) is shown for traceability.
- Priority: **P0** = MVP-blocking, **P1** = required for demo completeness, **P2** = nice-to-have / stretch.
- All API errors use one envelope: `{ "error": { "code": "<MACHINE_CODE>", "message": "<human text>", "details"?: {...} } }`.

### Epic A — Link Creation (Greenfield core)

| ID | Pri | Source | Requirement | Acceptance criteria |
|---|---|---|---|---|
| URL-FR-1.1 | P0 | FR-1 | Create short link | `POST /api/v1/links` with a valid URL and API key returns `201` and a body containing a 7-character code matching `^[A-Za-z0-9]{7}$`. |
| URL-FR-1.2 | P0 | FR-1 | Random code generation | Codes are generated from a CSPRNG over base62. On uniqueness collision, retry up to 5 times; if all fail return `503 CODE_GENERATION_EXHAUSTED` and emit a metric. Uniqueness is enforced by a DB unique constraint, not only an application check. |
| URL-FR-1.3 | P0 | FR-1 | No implicit dedupe | Submitting the same URL twice without dedupe returns two different codes. |
| URL-FR-1.4 | P1 | FR-1, Ambiguity log | Opt-in dedupe | When `dedupe=true` on the request (or the owner's default dedupe flag is on), and an **active, non-expired** link from the **same owner** exists for the same normalised URL (and same `expires_at`), return the existing link with `200`. Never dedupe across owners. |
| URL-FR-1.5 | P0 | NFR-4 | URL validation | Reject with `422 INVALID_URL` if: scheme not in allowlist (`http`, `https`); no host; length > 2048 chars; contains credentials (`user:pass@`); host resolves to the service's own short domain (redirect loop). |
| URL-FR-1.6 | P0 | NFR-3 | Durable write | `201` is returned only after the DB transaction commits. No accepted link may be lost on crash. |
| URL-FR-1.7 | P1 | — | Short URL in response | Response includes `short_url` built from configured `BASE_URL` + code. |

### Epic B — Custom Aliases

| ID | Pri | Source | Requirement | Acceptance criteria |
|---|---|---|---|---|
| URL-FR-2.1 | P0 | FR-2 | Accept custom alias | Optional `alias` 4–32 chars matching `^[A-Za-z0-9_-]{4,32}$`; on success the alias is the code. Invalid format → `422 INVALID_ALIAS`. |
| URL-FR-2.2 | P0 | FR-2 | Reserved words | Aliases equal (case-insensitive) to a reserved word are rejected with `422 RESERVED_ALIAS`. Initial list: `api`, `healthz`, `readyz`, `docs`, `redoc`, `openapi`, `openapi.json`, `static`, `admin`, `metrics`, `favicon.ico`, `robots.txt`. List is configuration, not code. |
| URL-FR-2.3 | P0 | FR-2 | Conflict | Alias already in use (active, inactive or expired) → `409 ALIAS_CONFLICT`. Aliases are never reused after deactivation (prevents hijacking old shared links). |
| URL-FR-2.4 | P1 | — | Namespace safety | Random codes and aliases share one namespace; a generated code can never shadow an alias (guaranteed by the unique constraint). |

### Epic C — Redirect (Hot Path)

| ID | Pri | Source | Requirement | Acceptance criteria |
|---|---|---|---|---|
| URL-FR-3.1 | P0 | FR-3 | Redirect | `GET /{code}` for an active, non-expired link returns `302` with `Location` = target. |
| URL-FR-3.2 | P0 | FR-3 | Error statuses | Unknown code → `404`; inactive (soft-deleted) → `404`; expired → `410`. Response bodies do not reveal owner or target. |
| URL-FR-3.3 | P0 | Ambiguity log | Non-cacheable redirect | Redirect responses carry `Cache-Control: private, no-cache, no-store` so browser caching does not hide clicks or defeat deactivation. |
| URL-FR-3.4 | P0 | NFR-1 | Cache-first lookup | Lookup order: Redis → DB. Cache entries hold `target`, `status`, `expires_at`; expiry is evaluated at request time. Cache TTL is bounded (default 24 h) and never exceeds `expires_at`. |
| URL-FR-3.5 | P0 | NFR-2 | Degrade, don't fail | If Redis errors/times out (timeout ≤ 5 ms budget), the request proceeds against the DB. If the analytics buffer is full or the pipeline is down, the click is dropped/counted as lost and the redirect still succeeds. |
| URL-FR-3.6 | P1 | NFR-1 | Negative caching | Unknown codes are negatively cached for a short TTL (default 60 s) to blunt enumeration and scan traffic; creation of that code invalidates the negative entry. |
| URL-FR-3.7 | P1 | NFR-4 | Redirect rate limit | Per-IP-hash rate limit on redirects (default 600 req/min) returns `429` with `Retry-After`. |

### Epic D — Expiry

| ID | Pri | Source | Requirement | Acceptance criteria |
|---|---|---|---|---|
| URL-FR-4.1 | P0 | FR-4 | Optional expiry | `expires_at` (ISO-8601 with timezone, stored UTC) optional; absent = never expires. |
| URL-FR-4.2 | P0 | FR-4 | Expiry window | `expires_at` must be in the future and ≤ now + 365 days; otherwise `422 INVALID_EXPIRY`. |
| URL-FR-4.3 | P0 | FR-3, FR-4 | Expired behaviour | After `expires_at`, redirect returns `410`; metadata endpoint reports `status = "expired"`. Status is derived at read time (no cron dependency for correctness). |

### Epic E — Link Metadata & Deactivation

| ID | Pri | Source | Requirement | Acceptance criteria |
|---|---|---|---|---|
| URL-FR-5.1 | P0 | FR-5 | Read metadata | `GET /api/v1/links/{code}` (owner API key) returns `code, short_url, target, created_at, expires_at, status (active\|inactive\|expired), total_clicks`. |
| URL-FR-5.2 | P0 | FR-5, NFR-4 | Owner-only read | Non-owner key → `404` (not `403`, to avoid confirming existence). Missing/invalid key → `401`. |
| URL-FR-5.3 | P1 | NFR-3 | Click count freshness | `total_clicks` is near-real-time; lag ≤ flush interval + 5 s under normal load. |
| URL-FR-6.1 | P0 | FR-6 | Soft delete | `DELETE /api/v1/links/{code}` by owner returns `204`, sets `status=inactive` and `deactivated_at`; row and analytics are retained. |
| URL-FR-6.2 | P0 | FR-6 | Owner only | Non-owner → `404`; missing key → `401`. Repeat delete is idempotent (`204`). |
| URL-FR-6.3 | P0 | FR-6 | Cache invalidation | Deactivation deletes/overwrites the cache entry in the same request; next redirect returns `404`. If Redis is down, the bounded TTL caps staleness and the event is logged and metered. |

### Epic F — Click Analytics (Async Pipeline)

| ID | Pri | Source | Requirement | Acceptance criteria |
|---|---|---|---|---|
| URL-FR-7.1 | P0 | FR-7, Ambiguity log | Click definition | Every successful `302` produces exactly one click event. `404`/`410`/`429` produce none. |
| URL-FR-7.2 | P0 | Ambiguity log | Event capture off hot path | Events are enqueued to a bounded in-memory buffer (non-blocking put). A background worker flushes in batches (default every 1 s or 500 events). Redirect latency is unaffected by flush. |
| URL-FR-7.3 | P0 | Ambiguity log | Privacy-preserving fields | Event stores: `code, ts (UTC), referrer_host, user_agent (truncated 512), is_bot, ip_hash = HMAC-SHA256(salt, ip), country (ISO-3166 alpha-2 or null)`. Raw IP is never persisted or logged. Salt comes from secret config and supports rotation. |
| URL-FR-7.4 | P0 | Ambiguity log | Bot flagging | Bots are flagged by user-agent rules (versioned list) and **not dropped**. |
| URL-FR-7.5 | P0 | FR-7 | Stats endpoint | `GET /api/v1/links/{code}/stats` (owner only) returns `total_clicks, human_clicks, bot_clicks, clicks_per_day[] (UTC days), top_referrers[≤10], top_countries[≤10] or null when geo unavailable`. Optional `from`/`to` query params, default last 30 days, max 365. |
| URL-FR-7.6 | P1 | NFR-3 | Bounded loss on crash | Loss is limited to the in-memory buffer at crash time; graceful shutdown (SIGTERM) flushes the buffer. Buffer overflow increments `clicks_dropped_total`. Behaviour is documented. |
| URL-FR-7.7 | P1 | — | Unique visitors estimate | Stats include `unique_visitors_estimate` (distinct `ip_hash` per day). |
| URL-FR-7.8 | P2 | — | Geo lookup | Country derived from a local offline GeoIP database if configured; otherwise `null`. No external network call on the hot path. |

### Epic G — Security & Access Control

| ID | Pri | Source | Requirement | Acceptance criteria |
|---|---|---|---|---|
| URL-NFR-4.1 | P0 | NFR-4 | API-key auth | All `/api/v1` endpoints require `X-API-Key`. Keys are stored hashed; comparison is constant-time. Redirect and health endpoints are public. |
| URL-NFR-4.2 | P0 | NFR-4 | Key provisioning | Keys are created via an admin CLI/seed script (no sign-up flow). Each key has `owner_id`, `label`, `created_at`, `revoked_at`. Revoked key → `401`. |
| URL-NFR-4.3 | P0 | NFR-4 | Write rate limits | Per-key limit on `POST` (default 60/min) → `429` with `Retry-After`. Uses Redis; falls back to an in-process limiter when Redis is down. |
| URL-NFR-4.4 | P0 | NFR-4 | No secrets in repo | Secrets via environment/secret store only; `.env.example` with placeholders; secret scanning in CI fails the build on detection. |
| URL-NFR-4.5 | P0 | NFR-4 | Input hardening | Request body size limit (e.g. 8 KB); strict JSON schema (unknown fields rejected); all DB access parameterised. |
| URL-NFR-4.6 | P1 | — | Security headers | API responses set `X-Content-Type-Options: nosniff`; errors never echo stack traces outside debug mode. |
| URL-NFR-4.7 | P2 | — | Target safety check | Optional denylist of target domains (config) rejected with `422 TARGET_BLOCKED`. |

### Epic H — Health, Observability & Operability

| ID | Pri | Source | Requirement | Acceptance criteria |
|---|---|---|---|---|
| URL-FR-8.1 | P0 | FR-8 | Liveness | `GET /healthz` returns `200` while the process is running; performs no dependency checks. |
| URL-FR-8.2 | P0 | FR-8 | Readiness | `GET /readyz` checks DB and Redis with short timeouts; returns `200` with per-dependency status when both are reachable, `503` otherwise (see OQ-6). |
| URL-OBS-1 | P0 | — | Structured logs | JSON logs with `request_id`, route, status, latency_ms, code (no IP, no API key). |
| URL-OBS-2 | P1 | — | Metrics | Expose: request latency histogram per route, cache hit ratio, Redis fallback count, code-generation retries, click buffer depth, clicks flushed/dropped, flush latency, 4xx/5xx counts. |
| URL-OBS-3 | P1 | — | Graceful shutdown | SIGTERM: stop accepting requests, flush click buffer, close pools, exit within 10 s. |

### Epic I — Engineering Quality, Testing & Documentation

| ID | Pri | Source | Requirement | Acceptance criteria |
|---|---|---|---|---|
| URL-NFR-5.1 | P0 | NFR-5 | Layered architecture | Modules separated into API (transport), service (business rules), repository (persistence), and infrastructure (cache, analytics worker). Service layer has no framework/HTTP imports. |
| URL-NFR-5.2 | P0 | NFR-5 | Type-checked | Static type checker runs in strict mode in CI with zero errors. |
| URL-NFR-5.3 | P0 | NFR-5 | Coverage | ≥85% line coverage on the service layer; CI fails below threshold. |
| URL-TEST-1 | P0 | — | Test pyramid | Unit tests (service rules), integration tests (real DB + Redis via containers), API contract tests against OpenAPI, failure-injection tests (Redis down, analytics down). |
| URL-TEST-2 | P0 | NFR-1 | Load test | Reproducible local load test script reports p50/p95/p99 for cache-hit and cache-miss redirects; results committed to `docs/perf/`. |
| URL-TEST-3 | P1 | FR-1..8 | Acceptance traceability | Each acceptance criterion in this PRD has ≥1 automated test tagged with its ID. |
| URL-DOC-1 | P0 | — | OpenAPI | Swagger UI served at `/docs`; schema includes examples and every error code. |
| URL-DOC-2 | P0 | NFR-3, NFR-6 | Operational docs | README (run locally), runbook (degraded modes, crash-loss window), scaling doc (horizontal scale path), ADRs for each decision in §7.1. |
| URL-NFR-6.1 | P0 | NFR-6 | Stateless app tier | No per-instance state required for correctness (buffer loss is the documented exception); multiple replicas can run behind a load balancer without code changes. |
| URL-NFR-6.2 | P1 | NFR-6 | Scale-out path doc | Document: replicas + LB, Redis as shared cache, DB read replicas for redirect misses, moving the click buffer to a durable queue/stream, analytics table partitioning by day. |

### Epic J — AI-Assisted Engineering Scenarios

These requirements govern **how** the system is built and evolved. Each scenario must be executed end to end and recorded (prompt/ticket → design note → PR → tests → review decision).

#### J.1 Workflow guardrails (apply to all scenarios)
| ID | Pri | Requirement | Acceptance criteria |
|---|---|---|---|
| URL-AI-1 | P0 | Human gates | AI may draft code, tests, docs, migrations and PR descriptions. Merge, migration execution and deployment require human action; branch protection enforces ≥1 human approval and green CI. |
| URL-AI-2 | P0 | Traceability | Every AI-assisted PR references a ticket ID from this PRD and lists the acceptance criteria it satisfies. |
| URL-AI-3 | P0 | Verification over trust | AI-generated changes must include or update tests; reviewers check tests fail without the change for bug fixes (regression proof). |
| URL-AI-4 | P1 | Scope control | One ticket per PR; diff size guideline ≤ 400 changed lines excluding generated files. |
| URL-AI-5 | P1 | Decision logging | Any interpretation of an ambiguous requirement is recorded as an ADR or in the ticket before implementation. |
| URL-AI-6 | P0 | Secret hygiene | AI tooling is never given production secrets; prompts and logs must not contain keys. |

#### J.2 Scenario 1 — Greenfield
| ID | Pri | Requirement | Acceptance criteria |
|---|---|---|---|
| URL-SCN-G1 | P0 | Build the MVP (Epics A–I) from this PRD via Detailed Design → tickets → AI-assisted implementation. | All P0 acceptance criteria pass in CI; load test meets NFR-1; demo script shows CUJ-1…CUJ-4. |

#### J.3 Scenario 2 — Brownfield (changes to the existing codebase)
Minimum three brownfield changes, one of each type:

| ID | Pri | Type | Change | Acceptance criteria |
|---|---|---|---|---|
| URL-SCN-B1 | P0 | Enhancement | Add owner-level default dedupe setting (URL-FR-1.4) after MVP ships, including a DB migration. | Migration is backward-compatible and reviewed by a human before execution; existing tests stay green; new tests cover both request flag and owner default. |
| URL-SCN-B2 | P0 | Bug fix | Seeded defect: expired links return `404` instead of `410` when served from cache. | A failing regression test is written first; fix makes it pass; root cause noted in PR. |
| URL-SCN-B3 | P1 | Refactor | Extract code generation behind a `CodeGenerator` interface (enables future sequence/hash strategies) with no behaviour change. | Public API unchanged; contract tests unchanged and green; coverage not reduced. |
| URL-SCN-B4 | P1 | Test & docs improvement | Raise service-layer coverage gaps and add failure-mode docs to the runbook. | Coverage report shows improvement; runbook reviewed. |

#### J.4 Scenario 3 — Ambiguous requirement
Input (deliberately vague): **"Make the analytics more accurate."**

| ID | Pri | Requirement | Acceptance criteria |
|---|---|---|---|
| URL-SCN-A1 | P0 | Interpret intent | AI produces a clarification note listing candidate interpretations (e.g. exclude bots from default totals, unique-visitor counts, timezone-correct daily buckets, reduce crash loss window) and their trade-offs. |
| URL-SCN-A2 | P0 | Normalise into an engineering problem | A human selects the interpretation(s); the result is written as tickets with testable criteria (e.g. "`/stats?exclude_bots=true` returns counts excluding `is_bot` events; default unchanged for backward compatibility"). |
| URL-SCN-A3 | P0 | Deliver | Selected tickets implemented following URL-AI-1…6; ADR records the decision. |

> The resolved-ambiguity table in §7.1 is itself the worked example of this process applied to the original requirements.

### 5.11 Risks, Trade-offs & Failure Scenarios

| # | Risk / failure scenario | Impact | Mitigation / guardrail | Validation |
|---|---|---|---|---|
| R1 | Redis down | Higher redirect latency; rate limits weaker | DB fallback; tight Redis timeout; in-process limiter; circuit breaker to stop hammering Redis | Failure-injection test; latency within miss budget |
| R2 | Analytics pipeline/DB slow | Hot-path slowdown if coupled | Non-blocking enqueue; bounded buffer; drop + meter on overflow | Test with flush blocked; redirect p95 unaffected |
| R3 | Process crash | Loss of buffered clicks | Small flush interval; SIGTERM flush; documented loss window; scale path to durable queue | Kill test; loss ≤ buffer size |
| R4 | Stale cache after deactivate | Deactivated link still redirects | Synchronous invalidation; bounded TTL; metric on invalidation failure | Integration test: delete → immediate 404 |
| R5 | Code collision | Create failure | DB unique constraint + retry; 62^7 ≈ 3.5 T space | Unit test with forced collisions |
| R6 | Code enumeration / scanning | Discovery of private links; load | Random (not sequential) codes; negative cache; redirect rate limit | Load test with random-code scan |
| R7 | Abuse (phishing / malware targets) | Reputational | API-key traceability; scheme allowlist; optional domain denylist; soft-delete retained for audit | Validation tests |
| R8 | Open-redirect / loop | Redirect to self; infinite loops | Reject targets on own domain | Validation test |
| R9 | PII exposure | Privacy breach | No raw IP storage/logging; salted HMAC; truncated UA | Log scan test; schema review |
| R10 | AI-introduced regressions or insecure code | Defects in main | Human review, CI gates (type, tests, coverage, secret scan, dependency scan), regression-test-first for bugs | Branch protection config audited |
| R11 | Alias squatting on reserved/system paths | Routing conflicts | Reserved word list; system routes registered before `/{code}` | Routing test |
| R12 | Clock skew affecting expiry | Early/late `410` | All comparisons in UTC using DB/server time; NTP assumed | Unit tests at boundary |

### 5.12 Analytics at Various Layers

| Layer | What is measured | Purpose |
|---|---|---|
| Product (click analytics) | Clicks, clicks/day, referrers, countries, bot share, unique visitors | Owner-facing value (FR-7) |
| Service / API | Per-route latency, error rates, 429s, create success rate | SLO tracking (NFR-1) |
| Cache | Hit ratio, fallback count, invalidation failures | Validates hot-path design |
| Pipeline | Buffer depth, flush batch size/latency, dropped events | Validates NFR-2/NFR-3 |
| Data | Row growth, query latency for stats | Capacity planning (NFR-6) |
| Engineering process | PR lead time, % AI-assisted PRs, review rework rate, defects escaped, coverage trend | Evaluates AI-assisted workflow |

---

## 6. Success Metrics & KPIs

| Category | KPI | Target |
|---|---|---|
| Performance | Redirect p95, cache hit (local load test) | < 20 ms |
| Performance | Redirect p95, cache miss | < 50 ms |
| Resilience | Redirect success rate with Redis down / analytics down | 100% of valid codes resolve |
| Durability | Accepted links lost in crash tests | 0 |
| Durability | Clicks lost per crash | ≤ buffer contents at crash (documented bound) |
| Analytics freshness | Click visible in stats after redirect | ≤ 5 s p95 |
| Quality | Service-layer line coverage | ≥ 85% |
| Quality | Type-check errors / secret-scan findings | 0 / 0 |
| Correctness | P0 acceptance criteria with passing automated tests | 100% |
| Security | Endpoints requiring auth that accept unauthenticated calls | 0 |
| AI workflow | Scenarios completed end to end (greenfield, brownfield, ambiguous) | 3 of 3 |
| AI workflow | Merges/migrations/deploys performed by AI without human action | 0 |
| AI workflow | AI-assisted PRs with requirement traceability | 100% |

---

## 7. Open Questions & Assumptions

### 7.1 Decisions already resolved (from ambiguity log)

| Question | Decision | Why |
|---|---|---|
| 301 or 302 redirect? | 302 by default | 301 is cached by browsers, which hides clicks and makes deactivation ineffective. |
| Who can create links? | Anyone with an API key; redirects are public | Keeps abuse traceable without user accounts. |
| Deduplicate identical URLs? | No by default; opt-in flag per owner | Different owners need separate analytics on the same target. |
| Code length and alphabet? | 7 chars, base62 | ≈ 3.5 trillion codes; collisions rare and retried. |
| What counts as a click? | Every successful redirect; bots flagged, not dropped | Raw data kept; filtering is a reporting choice. |
| Store IP addresses? | No; salted hash + coarse country | Minimises PII, enough for unique-visitor estimates. |
| Real-time analytics? | Near-real-time (seconds) | Allows batching off the hot path. |

### 7.2 Assumptions made in this PRD (defaults; confirm in Detailed Design)

| ID | Assumption |
|---|---|
| A1 | Persistence is a relational DB (PostgreSQL assumed) as the source of truth; Redis is cache + rate-limit store only and is never required for correctness. |
| A2 | Implementation language/framework will be chosen in Detailed Design; it must support async I/O, OpenAPI generation and strict static typing. |
| A3 | Codes and aliases are **case-sensitive** for resolution; reserved-word matching is **case-insensitive**. |
| A4 | Dedupe is supported both as a per-request flag (FR-1 wording) and as an owner default (ambiguity log wording); request flag overrides owner default. |
| A5 | URL normalisation for dedupe: lowercase scheme and host, remove default port, keep path/query/fragment as-is. |
| A6 | Absent `expires_at` means no expiry. |
| A7 | Metadata and stats are visible to the owner only; non-owners get `404`. |
| A8 | Default rate limits: 60 creates/min per key; 600 redirects/min per IP hash. Tunable by config. |
| A9 | Click flush interval 1 s or 500 events; buffer capacity 10,000 events. |
| A10 | Daily buckets use UTC. |
| A11 | Country lookup uses an optional offline GeoIP DB; absent → `null`. |
| A12 | Single-region deployment; local load test on a developer-class machine is the NFR-1 reference environment. |
| A13 | Click and link data retained indefinitely for the prototype. |

### 7.3 Open questions (for team alignment before/during Detailed Design)

| ID | Question | Owner | Default if unresolved |
|---|---|---|---|
| OQ-1 | Should owners be able to reactivate a soft-deleted link, or is deactivation final? | Product | Final (no endpoint). |
| OQ-2 | Should `GET /api/v1/links` (list my links, paginated) be in scope? Not in FR list but common for integrators. | Product | Out of MVP; P2 backlog. |
| OQ-3 | Should `/stats` default to human-only counts or all clicks? | Product | All clicks, with human/bot split. |
| OQ-4 | Data retention period and deletion policy for click events (privacy)? | Product + Legal | Indefinite for prototype; revisit before any production use. |
| OQ-5 | Salt rotation cadence for IP hashing, given rotation breaks unique-visitor continuity? | Security | Rotate manually; document impact. |
| OQ-6 | Should `/readyz` fail when only Redis is down, given redirects still work (NFR-2)? Failing would remove healthy pods from the LB. | Architecture | Report Redis as `degraded` but return `200`; return `503` only when DB is unreachable. **Conflicts with FR-8 literal wording — confirm.** |
| OQ-7 | Should aliases be case-insensitive to avoid look-alike confusion (`Promo` vs `promo`)? | Product | Case-sensitive (A3). |
| OQ-8 | Is a 307/308 option needed for non-GET clients? | Architecture | No; 302 only. |
| OQ-9 | Which load-testing tool and hardware profile define "local load test" for sign-off? | Engineering | Decided in Detailed Design and recorded in `docs/perf/`. |

---

## Appendix A — API Surface Summary (input to Detailed Design)

| Method | Path | Auth | Success | Errors |
|---|---|---|---|---|
| POST | `/api/v1/links` | API key | 201 (200 on dedupe hit) | 401, 409, 413, 422, 429, 503 |
| GET | `/api/v1/links/{code}` | API key (owner) | 200 | 401, 404 |
| DELETE | `/api/v1/links/{code}` | API key (owner) | 204 | 401, 404 |
| GET | `/api/v1/links/{code}/stats` | API key (owner) | 200 | 401, 404, 422 |
| GET | `/{code}` | Public | 302 | 404, 410, 429 |
| GET | `/healthz` | Public | 200 | — |
| GET | `/readyz` | Public | 200 | 503 |
| GET | `/docs` | Public | 200 (Swagger UI) | — |

## Appendix B — Traceability Matrix (source → PRD IDs)

| Source | PRD IDs |
|---|---|
| FR-1 | URL-FR-1.1 … 1.7 |
| FR-2 | URL-FR-2.1 … 2.4 |
| FR-3 | URL-FR-3.1 … 3.7 |
| FR-4 | URL-FR-4.1 … 4.3 |
| FR-5 | URL-FR-5.1 … 5.3 |
| FR-6 | URL-FR-6.1 … 6.3 |
| FR-7 | URL-FR-7.1 … 7.8 |
| FR-8 | URL-FR-8.1, 8.2 |
| NFR-1 | URL-FR-3.4, 3.6, URL-TEST-2 |
| NFR-2 | URL-FR-3.5, CUJ-4, R1, R2 |
| NFR-3 | URL-FR-1.6, 7.6, URL-DOC-2 |
| NFR-4 | URL-FR-1.5, URL-NFR-4.1 … 4.7 |
| NFR-5 | URL-NFR-5.1 … 5.3, URL-TEST-1, 3 |
| NFR-6 | URL-NFR-6.1, 6.2 |
| Goal 3 (scenarios) | URL-AI-1 … 6, URL-SCN-G1, B1 … B4, A1 … A3 |

## Appendix C — Suggested JIRA Structure

- **Epics:** A Link Creation · B Custom Aliases · C Redirect · D Expiry · E Metadata & Deactivation · F Click Analytics · G Security · H Health & Observability · I Quality, Testing & Docs · J AI-Assisted Scenarios
- **Stories:** one per PRD ID above; story description = requirement; acceptance criteria copied verbatim.
- **Labels:** `greenfield` / `brownfield` / `ambiguous`, `P0`/`P1`/`P2`, `ai-assisted`.
- **Definition of Done:** acceptance tests tagged with the ID pass in CI; type check clean; coverage gate met; docs updated; human-approved PR.
