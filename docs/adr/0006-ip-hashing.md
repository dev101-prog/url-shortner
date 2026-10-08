# ADR 0006: store only an HMAC of the client IP
- Status: Accepted (PRD §7.1, URL-FR-7.3) · Date: 2026-10-07
## Decision
`ip_hash = HMAC-SHA256(app.analytics.ip-salt, ip)` as lowercase hex; the raw IP is never stored or
logged. The salt is a secret outside local; startup fails with the placeholder salt (§13.3).
## Consequences
Unique-visitor estimates without PII. Rotating the salt breaks visitor continuity (PRD OQ-5).
