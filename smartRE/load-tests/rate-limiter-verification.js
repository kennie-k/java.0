import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const TOKEN = __ENV.TEST_TOKEN || '';

const rateLimited429s = new Counter('rate_limited_429s');

export const options = {
  scenarios: {
    burst_single_user: {
      executor: 'constant-arrival-rate',
      rate: 150,
      timeUnit: '1s',
      duration: '10s',
      preAllocatedVUs: 50,
      maxVUs: 150,
    },
  },
};

export default function () {
  const params = TOKEN ? { headers: { Authorization: `Bearer ${TOKEN}` } } : {};
  const res = http.get(`${BASE_URL}/api/properties/search?size=5`, params);
  if (res.status === 429) rateLimited429s.add(1);
  check(res, {
    'status is 200 or 429': r => r.status === 200 || r.status === 429,
  });
  sleep(0.05);
}

export function handleSummary(data) {
  const total = data.metrics.http_reqs ? data.metrics.http_reqs.values.count : 0;
  const limited = data.metrics.rate_limited_429s ? data.metrics.rate_limited_429s.values.count : 0;
  console.log(`Total requests: ${total}, rate-limited (429): ${limited}`);
  console.log(limited > 0
    ? 'Rate limiter is active and rejecting excess traffic as documented.'
    : 'No 429s observed - either under threshold or rate limiter is not enforcing.');
  return {};
}
