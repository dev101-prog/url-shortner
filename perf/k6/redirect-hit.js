// Cache-hit redirect load test (design §8.6, URL-TEST-2).
// Setup creates LINKS links through the API, then VUs request random existing codes. Redirects are
// not followed (k6 option redirects: 0), so a 302 is a success. All redirect requests share one
// metric name (URL grouping) to keep the number of time series bounded.
// Env: BASE_URL (default http://localhost:8080), API_KEY (default demo-key-alice-0001),
//      LINKS (1000), VUS (200), RAMP (2m), STEADY (5m).
import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const API_KEY = __ENV.API_KEY || 'demo-key-alice-0001';
const LINKS = parseInt(__ENV.LINKS || '1000', 10);

export const options = {
  maxRedirects: 0,
  setupTimeout: '5m',
  scenarios: {
    hit: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: __ENV.RAMP || '2m', target: parseInt(__ENV.VUS || '200', 10) },
        { duration: __ENV.STEADY || '5m', target: parseInt(__ENV.VUS || '200', 10) },
      ],
      gracefulRampDown: '10s',
    },
  },
  thresholds: {
    'http_req_duration{scenario:hit}': ['p(95)<20'],
    'http_req_failed{scenario:hit}': ['rate<0.001'],
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

export function setup() {
  const codes = [];
  for (let i = 0; i < LINKS; i++) {
    const res = http.post(
      `${BASE_URL}/api/v1/links`,
      JSON.stringify({ url: `https://example.org/k6/hit/${Date.now()}/${i}` }),
      { headers: { 'Content-Type': 'application/json', 'X-API-Key': API_KEY } },
    );
    if (res.status !== 201) {
      throw new Error(`setup create failed: ${res.status} ${res.body}`);
    }
    codes.push(res.json('code'));
  }
  return { codes };
}

export default function (data) {
  const code = data.codes[Math.floor(Math.random() * data.codes.length)];
  const res = http.get(`${BASE_URL}/${code}`, { redirects: 0, tags: { name: 'GET /{code}' } });
  check(res, { 'status is 302': (r) => r.status === 302 });
}
