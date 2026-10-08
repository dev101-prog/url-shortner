# ADR 0001: 302 redirects, never cacheable
- Status: Accepted (PRD §7.1, design D6) · Date: 2026-10-07
## Context
301 responses are cached by browsers, hiding clicks and making deactivation ineffective.
## Decision
`GET /{code}` answers `302 Found` with `Cache-Control: private, no-cache, no-store, max-age=0` and
`Pragma: no-cache` (URL-FR-3.1, 3.3).
## Consequences
Every click reaches the service (accurate analytics, immediate deactivation) at the cost of
browser-side caching.
