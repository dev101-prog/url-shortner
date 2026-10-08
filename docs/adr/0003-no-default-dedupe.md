# ADR 0003: no implicit dedupe; opt-in per request
- Status: Accepted (PRD §7.1, URL-FR-1.3, 1.4) · Date: 2026-10-07
## Decision
The same URL submitted twice yields two codes. With `dedupe=true` (and no alias), an active,
unexpired, non-alias link of the same owner with the same normalised URL and the same `expires_at`
is returned with 200. Never across owners. The owner-level default is brownfield scenario B1.
## Consequences
Separate analytics per link by default; dedupe is explicit.
