# Scaling path (URL-NFR-6.1, 6.2; design §13.4)

The app tier is stateless for correctness. The only per-instance state is the in-process cache,
the rate-limit counters and the click buffer, each behind a port interface so it can be replaced
without touching services.

| Step | Change | Code impact | Removes |
|---|---|---|---|
| 1 | Run N replicas behind a load balancer | none | single-instance throughput limit |
| 2 | Replace `CaffeineLinkCache` with a Redis implementation of `LinkCache`; invalidate through Redis pub/sub. Replace `CaffeineRateLimiter` with a Redis-backed `RateLimiter` | new infra classes, same ports | cross-instance staleness after deactivation (ADR 0004); N x rate limit (ADR 0008) |
| 3 | Postgres read replica for redirect misses and stats queries | route read-only repositories to a second data source | DB read load on the primary |
| 4 | Replace `ClickBuffer` with a durable stream (Kafka or Redis Streams) behind `ClickSink` | new `ClickSink` and consumer | crash loss of buffered clicks (URL-FR-7.6) |
| 5 | Partition `click_events` by day and add a `daily_link_stats` rollup table | migration plus stats queries | stats cost on very large tables |

Measured baseline (one instance, laptop): see `docs/perf/README.md`.

Operational notes for multi-instance deployments:
- Flyway runs from a separate, human-approved step; set `spring.flyway.enabled=false` on the app.
- Readiness checks only the database (ADR 0009), so a cache outage never removes healthy pods.
- `/actuator/prometheus` must be restricted outside local environments.
