# ADR 0010: analytics accuracy = opt-in bot exclusion + range-level unique visitors

- Status: Accepted on branch `feat/a-analytics-accuracy` (scenario A2, design §11.4); contract
  change pending tech lead sign-off
- Date: 2026-10-08

## Context
The request "make the analytics more accurate" was ambiguous. The interpretation note
([docs/scenarios/A1.md](../scenarios/A1.md)) listed five candidates (I1-I5) with trade-offs.

## Decision
Implement **I1 + I2** as two well-defined tickets:
- **URL-FR-7.9**: `GET /api/v1/links/{code}/stats?exclude_bots=true` returns all totals, per-day
  counts, referrers and countries computed over `is_bot = false` events only. When the parameter
  is absent or `false`, the response is byte-identical to the current output (the default code
  path and its §4.5 SQL are unchanged; the human-only path uses separate queries).
- **URL-FR-7.10**: `unique_visitors_estimate` is `count(DISTINCT ip_hash)` over the whole range,
  not the sum of daily values. This is already how `main` computes it; the ticket makes it an
  explicit, tested contract (the seed gives 3 for `aB3dE7x`). Caveat: rotating the IP salt breaks
  continuity across the rotation (PRD OQ-5).

## Deferred
- I3 (fewer lost clicks): needs a durable queue; already the scale path in `docs/scaling.md`.
- I4 (owner time zones): more complex SQL; UTC remains the documented default (ADR 0007).
- I5 (500 ms flush): not chosen; doubles flush writes for marginal freshness.

## Consequences
An additive, backward-compatible API change (one optional query parameter) that needs a tech
lead-approved OpenAPI baseline update (design §10.5). Bots are still flagged, never dropped
(URL-FR-7.4).
