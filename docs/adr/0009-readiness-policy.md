# ADR 0009: readiness checks the database only
- Status: Accepted (design §6.8, §16.3; resolves PRD OQ-6) · Date: 2026-10-07
## Decision
`/healthz` performs no checks. `/readyz` returns 200 when Postgres answers within 1 s and 503
the standard error envelope with code `NOT_READY` (design §5.2) and
`details.checks.db = "DOWN"` otherwise (human decision 2026-10-08, reconciling §5.2 with §5.3).
Cache problems never affect readiness because they degrade to the database.
## Consequences
Pods are removed from the load balancer only when they cannot serve correctly. This deviates from
the literal PRD FR-8.2 wording (Redis check); there is no Redis in this design.
