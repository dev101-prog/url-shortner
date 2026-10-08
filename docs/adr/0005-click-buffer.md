# ADR 0005: bounded in-memory click buffer with scheduled batch flush
- Status: Accepted (design D5, §3.6) · Date: 2026-10-07
## Decision
Redirects `offer()` click events to an `ArrayBlockingQueue` (10,000) without blocking. A worker
flushes up to 500 events every second in one transaction (events + `click_count`), retries once,
then drops and counts. Graceful shutdown drains the buffer.
## Consequences
Redirect latency is independent of analytics. Up to the buffer contents (~1 s of traffic) can be
lost on a crash (URL-FR-7.6); the durable-stream scale path removes this.
