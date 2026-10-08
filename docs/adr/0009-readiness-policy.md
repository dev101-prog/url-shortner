# ADR 0009: readiness checks the database only
- Status: Accepted (design §6.8, §16.3; resolves PRD OQ-6) · Date: 2026-10-07
## Decision
`/healthz` performs no checks. `/readyz` returns 200 when Postgres answers within 1 s and 503
`{"status":"DOWN","checks":{"db":"DOWN"}}` otherwise. Cache problems never affect readiness
because they degrade to the database.
## Consequences
Pods are removed from the load balancer only when they cannot serve correctly. This deviates from
the literal PRD FR-8.2 wording (Redis check); there is no Redis in this design.
