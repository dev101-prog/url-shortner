// Cache-miss redirect load test (design §8.6, URL-TEST-2): random codes that do not exist, so every
// request goes to Postgres. Run the app with the negative cache disabled (APP_CACHE_NEGATIVETTL=PT0S),
// otherwise repeated codes would be answered from the negative cache.
// A 404 is the expected response here, so it is not counted as a failed request.
// Env: BASE_URL (default http://localhost:8080), VUS (200), RAMP (2m), STEADY (5m).
import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const ALPHABET = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789';

http.setResponseCallback(http.expectedStatuses(404));

export const options = {
  maxRedirects: 0,
  scenarios: {
    miss: {
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
    'http_req_duration{scenario:miss}': ['p(95)<50'],
    'http_req_failed{scenario:miss}': ['rate<0.001'],
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

// 9 characters: valid for the /{code} route, never a generated (7-char) code
function randomCode() {
  let code = '';
  for (let i = 0; i < 9; i++) {
    code += ALPHABET[Math.floor(Math.random() * ALPHABET.length)];
  }
  return code;
}

export default function () {
  const res = http.get(`${BASE_URL}/${randomCode()}`, { redirects: 0, tags: { name: 'GET /{code}' } });
  check(res, { 'status is 404': (r) => r.status === 404 });
}
