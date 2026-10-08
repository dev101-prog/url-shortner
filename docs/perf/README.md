# Performance results (URL-TEST-2, design §8.6)

## Environment (2026-10-08, commit d6dcd1c)
- Host: MacBook Pro, Intel Core i7-9750H @ 2.60 GHz (6 cores / 12 threads), 32 GB RAM, macOS 15.6.
- Docker Desktop 28.3.3, VM with 12 CPUs and 8 GB RAM. App, Postgres 16.4 and k6 0.54.0 all run
  as containers on the same host and the same compose network.
- App image built from `Dockerfile` (Temurin 21 JRE), profile `local`, JSON access logs at INFO.
- The 2026-10-08 numbers were measured with the equivalent `docker run` setup used before
  `perf/compose.perf.yml` existed (same image, same overrides, same network topology).

## Perf-only overrides (not changes to the defaults)
The default limits are correct for production but make the §8.6 profile impossible from one load
generator, so perf runs start the app with [`perf/compose.perf.yml`](../../perf/compose.perf.yml):

| Override | Why |
|---|---|
| `APP_RATELIMIT_REDIRECTPERMINUTE=100000000` | 200 VUs share one client IP; 600/min per IP hash would turn most requests into 429s |
| `APP_RATELIMIT_CREATEPERMINUTE=100000` | setup creates 1,000 links with one key (default 60/min) |
| `APP_CACHE_NEGATIVETTL=PT0S` | miss scenario: §8.6 requires the negative cache to be disabled |
| `LOGGING_LEVEL_COM_EXAMPLE_URLSHORTENER=INFO` | the `local` profile sets DEBUG |

## How to run (any folder, any host: no bind mounts, no host networking)
k6 runs as the `k6` compose service (profile `test`) on the compose network and reads the script
from stdin. `--no-deps` keeps the already-running app (and its overrides) untouched.

```bash
docker compose -f docker-compose.yml -f perf/compose.perf.yml up --build -d     # app with perf overrides
docker compose run --rm --no-deps -T k6 run -e BASE_URL=http://app:8080 - < perf/k6/redirect-hit.js
docker compose run --rm --no-deps -T k6 run -e BASE_URL=http://app:8080 - < perf/k6/redirect-miss.js
# same thing via the wrapper; extra k6 args are passed through (e.g. a short smoke run):
bash scripts/k6.sh perf/k6/redirect-hit.js -e VUS=5 -e RAMP=5s -e STEADY=10s -e LINKS=20
docker compose up -d                                                             # back to default limits
```
Add `--summary-export` only if you mount an output folder; the results below were copied from the
k6 summaries.

## Profile
2-minute ramp to 200 VUs, 5-minute steady phase, `redirects: 0`, no think time (closed loop).

## Results

| Scenario | Requests | Throughput | p50 | p95 | p99 | Failed | Threshold | Result |
|---|---|---|---|---|---|---|---|---|
| hit (`2026-10-08-d6dcd1c-hit.json`) | 4,275,201 | 10,084 req/s | 13.26 ms | **39.64 ms** | 71.12 ms | 0 % | p95 < 20 ms | **FAIL** |
| miss (`2026-10-08-d6dcd1c-miss.json`) | 2,142,574 | 5,101 req/s | 28.43 ms | **77.82 ms** | 119.52 ms | 0 % | p95 < 50 ms | **FAIL** |

An earlier hit run (before the scripts grouped URLs under one metric name) measured p95 30.89 ms at
12,022 req/s, so run-to-run variance on this machine is large.

**NFR-1 is not met on this developer laptop.** No thresholds or code were tuned. Likely
contributors, to investigate before release (G11): the closed-loop 200 VUs with no think time
saturate a shared 12-CPU Docker VM that runs app, database and load generator together; one INFO
access-log line per request; Docker Desktop networking. The next step is to re-run on the
reference hardware with think time agreed with the product owner, and profile the app if it
still misses.
