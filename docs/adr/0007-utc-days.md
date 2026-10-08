# ADR 0007: UTC day buckets
- Status: Accepted (PRD A10, design §4.5) · Date: 2026-10-07
## Decision
All timestamps are TIMESTAMPTZ; stats bucket by `(clicked_at AT TIME ZONE 'UTC')::date` and treat
`from`/`to` as inclusive UTC dates. Default range: last 30 days ending today (UTC); max 365 days.
## Consequences
Deterministic, timezone-independent numbers. Owner-local days are a possible future option (A-scenario I4).
