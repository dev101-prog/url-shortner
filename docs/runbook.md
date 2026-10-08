# Runbook: URL Shortener

Operational guide for the prototype (design §6.8, §6.9, §13). The commands below were run locally
on 2026-10-08, including stopping and restarting Postgres under the running app.

## Start, stop, reset
```bash
docker compose up --build -d          # postgres + app (.env sets COMPOSE_PROFILES=full)
curl -s localhost:8080/readyz         # {"status":"UP","checks":{"db":"UP"}}
docker compose down                   # stop, keep data
docker compose down -v                # stop and DELETE the database volume (re-seeds on next start)
```
Developer mode: `docker compose up -d postgres` then `./mvnw spring-boot:run -Dspring-boot.run.profiles=local`.

## Probes
| Probe | Meaning | Use as |
|---|---|---|
| `GET /healthz` | process alive, no dependency checks | liveness |
| `GET /readyz` | Postgres answers within 1 s (`app.health.db-timeout`) | readiness; otherwise 503 error envelope `{"error":{"code":"NOT_READY",...,"details":{"checks":{"db":"DOWN"}}}}` |

## Failure modes (design §6.8)

| Failure | Symptoms | Metrics / logs to check | Redirect | Create / API | Analytics | Readiness | Recovery |
|---|---|---|---|---|---|---|---|
| Cache throws errors | higher redirect latency | `urlshortener_cache_errors_total`, WARN `Link cache ... failed` | works (DB fallback) | works (cache warm skipped) | works | 200 | none needed; investigate the cause; restart the instance if errors persist |
| Flush failing (analytics write error) | `total_clicks` stops growing | `urlshortener_clicks_dropped_total{reason="flush_failed"}`, WARN `Dropped N click events` | **works** | works | clicks dropped and counted | 200 | fix the DB issue; flushing resumes automatically |
| Click buffer full | same as above | `urlshortener_clicks_dropped_total{reason="buffer_full"}`, `urlshortener_clicks_buffer_depth` near 10,000 | **works** | works | clicks dropped and counted | 200 | check flush latency (`urlshortener_clicks_flush_duration`) and DB health |
| Postgres down | 500 on API and on redirect misses | `/readyz` 503, `hikaricp_connections_pending`, Hikari timeouts in logs | cache hits keep redirecting until TTL (10 min); misses 500 | 500 | flush retries once, then drops | 503 | `docker compose start postgres`; the app reconnects without a restart (verified: /readyz back to 200 about 2 s after Postgres started, create 201) |
| Process crash | instance gone | container restarts | – | committed links are safe (URL-FR-1.6) | buffered clicks lost (≤ 1 s typical, ≤ 10,000 events) | – | restart; no data repair needed |

### Failure modes: diagnosis and recovery commands (scenario B4)
Every command below was run against `docker compose up --build -d` on 2026-10-08; the outputs
shown are what it printed.

| Step | Command | What to look for |
|---|---|---|
| Probes | `curl -s localhost:8080/healthz; curl -s localhost:8080/readyz` | `{"status":"UP"}`; readiness `{"checks":{"db":"UP"},"status":"UP"}`. When the DB is down: 503 `{"error":{"code":"NOT_READY",...,"details":{"checks":{"db":"DOWN"}}}}` |
| Key metrics | `curl -s localhost:8080/actuator/prometheus \| grep -E '^(urlshortener_(cache_errors\|clicks_dropped\|clicks_buffer_depth\|clicks_flushed\|negative_cache_hits\|codegen_exhausted)\|hikaricp_connections_pending)'` | non-zero `cache_errors_total`, `clicks_dropped_total{reason=...}`, a buffer depth near 10,000, `hikaricp_connections_pending` > 0 |
| Rate limiting | `curl -s localhost:8080/actuator/prometheus \| grep '^urlshortener_ratelimit_denied'` | appears after the first 429, e.g. `urlshortener_ratelimit_denied_total{bucket="create"} 2.0` |
| Recent problems | `docker compose logs app --since 10m --no-log-prefix \| grep -E '"level":"(WARN\|ERROR)"'` | WARN lines name the failing component (`Link cache ... failed`, `Dropped N click events`, `Readiness database check failed`) |
| Trace one request | send `X-Request-Id: <id>`, then `docker compose logs app --no-log-prefix \| grep '"request_id":"<id>"'` | the access-log line with `route`, `status`, `latency_ms` (never IPs or keys) |
| DB outage drill | `docker compose stop postgres` → `curl -s localhost:8080/readyz` → `docker compose start postgres` | 503 `NOT_READY` while stopped; back to 200 about 1 s after start, with no app restart |
| Graceful drain | `docker compose stop app && docker compose logs app --no-log-prefix \| grep -E 'drained on shutdown\|Graceful shutdown complete'` then `docker compose start app` | `Click buffer drained on shutdown: N events`, `Graceful shutdown complete` |

Recovery summary: cache errors and flush failures need no action on the app (it degrades and
recovers by itself); fix the database, then confirm `/readyz` is 200 and
`urlshortener_clicks_dropped_total` stops increasing. Restart the app only if readiness stays 503
while Postgres is healthy.

### Crash-loss window (URL-FR-7.6)
Clicks wait in an in-memory buffer (capacity 10,000) for at most one flush interval (1 s). A crash
loses at most the buffer contents. A graceful stop (SIGTERM, `docker compose stop`) drains the
buffer before the pool closes; the log shows `Click buffer drained on shutdown: N events`.

### Deactivated link still redirects
With one instance, `DELETE` evicts the cache entry immediately. With several instances, other
instances may serve the link until their cache TTL (10 min) expires (ADR 0004). Scale path:
shared cache with invalidation (`docs/scaling.md`).

### Rate limiting (429)
`urlshortener_ratelimit_denied_total{bucket="create|redirect"}`. Defaults: 60 creates/min per API
key, 600 redirects/min per IP hash. Limits are per instance (ADR 0008).

## Secrets and configuration
- `APP_IP_SALT` must be set outside the `local` profile; startup fails with the placeholder
  `change-me` (design §13.3). Rotating it breaks unique-visitor continuity (PRD OQ-5).
- `SPRING_DATASOURCE_PASSWORD` is empty only for local `trust` auth (design §16.2).
- Never log or paste raw API keys or client IPs. Logs contain `request_id`, route templates,
  status, latency and owner ids only.

## API keys
```bash
bash scripts/create-api-key.sh <owner-name> [label]   # prints the raw key once
docker exec urlshortener-db psql -U postgres -d urlshortener \
  -c "UPDATE api_keys SET revoked_at = now() WHERE key_prefix = '<first 12 chars>'"   # revoke
```
A revoked key stops working within 30 s (positive auth cache TTL).
