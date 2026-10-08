# ADR 0008: fixed-window rate limiter in Caffeine
- Status: Accepted (design D8) · Date: 2026-10-07
## Decision
Counters keyed `bucket:key:minute` with one-minute windows: 60 creates/min per API key, 600
redirects/min per IP hash; 429 with `Retry-After` until the window ends. If the counter store fails,
requests are allowed and the failure is counted.
## Consequences
Readable and dependency-free. Bursts up to 2x at window edges; limits are per instance (N
instances allow N x the limit) until a Redis-backed limiter replaces it.
