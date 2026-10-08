# ADR 0002: API-key authentication with SHA-256 hashes
- Status: Accepted (design D7) · Date: 2026-10-07
## Context
Integrators need traceable ownership without a sign-up flow (PRD anti-goals).
## Decision
`/api/v1/**` requires `X-API-Key`. Keys are high-entropy random values, stored only as SHA-256 hex
and looked up by hash; positive results are cached for 30 s. Keys are provisioned with
`scripts/create-api-key.sh`; revocation sets `revoked_at`. Redirect and health endpoints are public.
## Consequences
Fast per-request auth; a revoked key may work for up to 30 s. An unsalted fast hash is acceptable
only because keys are random 256-bit values, not passwords.
