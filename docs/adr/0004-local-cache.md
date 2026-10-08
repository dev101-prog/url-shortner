# ADR 0004: in-process Caffeine cache instead of Redis
- Status: Accepted (design D4, §16.3) · Date: 2026-10-07
## Decision
Links and the negative cache live in Caffeine behind the `LinkCache` port. TTL is
`min(10 min, expiresAt - now)` while the expiry is in the future; already-expired entries use the
normal TTL because status is evaluated per request. Cache errors degrade to the database.
## Consequences
Simple prototype with one container fewer. With several instances a deactivated link can be served
by other instances for up to 10 minutes; the scale path is a shared cache (docs/scaling.md).
