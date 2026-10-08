# Performance results (URL-TEST-2, design §8.6)

## Environment (2026-10-08, commit d6dcd1c)
- Host: MacBook Pro, Intel Core i7-9750H @ 2.60 GHz (6 cores / 12 threads), 32 GB RAM, macOS 15.6.
- Docker Desktop 28.3.3, VM with 12 CPUs and 8 GB RAM. App, Postgres 16.4 and k6 0.54.0 all run
  as containers on the same host and the same compose network (`myworkdir_default`).
- App image built from `Dockerfile` (Temurin 21 JRE), profile `local`, JSON access logs at INFO.

## Perf-only overrides (not changes to the defaults)
The default limits are correct for production but make the §8.6 profile impossible from one load
generator, so the perf run used environment overrides on a separate container from the same image:

| Override | Why |
|---|---|
| `APP_RATELIMIT_REDIRECTPERMINUTE=100000000` | 200 VUs share one client IP; 600/min per IP hash would turn most requests into 429s |
| `APP_RATELIMIT_CREATEPERMINUTE=100000` | setup creates 1,000 links with one key (default 60/min) |
| `APP_CACHE_NEGATIVETTL=PT0S` | miss scenario: §8.6 requires the negative cache to be disabled |
| `LOGGING_LEVEL_COM_EXAMPLE_URLSHORTENER=INFO` | the `local` profile sets DEBUG |

```bash
docker compose stop app
docker run -d --name urlshortener-perf --network myworkdir_default -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=local -e SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/urlshortener \
  -e APP_BASE_URL=http://localhost:8080 -e APP_RATELIMIT_CREATEPERMINUTE=100000 \
  -e APP_RATELIMIT_REDIRECTPERMINUTE=100000000 -e APP_CACHE_NEGATIVETTL=PT0S \
  -e LOGGING_LEVEL_COM_EXAMPLE_URLSHORTENER=INFO myworkdir-app
docker run --rm -i --network myworkdir_default -e BASE_URL=http://urlshortener-perf:8080 \
  -v "$PWD/perf/k6:/scripts:ro" grafana/k6:0.54.0 run /scripts/redirect-hit.js   # then redirect-miss.js
docker rm -f urlshortener-perf && docker compose up -d
```
(On macOS, Docker Desktop cannot mount folders under `~/Desktop`; copy the scripts to `/tmp` first.)

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
