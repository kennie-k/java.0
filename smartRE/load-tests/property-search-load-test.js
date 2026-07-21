import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

const errorRate = new Rate('errors');
const searchDuration = new Trend('search_duration');
const detailDuration = new Trend('detail_duration');

export const options = {
  scenarios: {
    property_search: {
      executor: 'ramping-vus',
      exec: 'searchProperties',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 50 },
        { duration: '1m', target: 200 },
        { duration: '2m', target: 200 },
        { duration: '30s', target: 0 },
      ],
    },
    property_detail: {
      executor: 'ramping-vus',
      exec: 'viewPropertyDetail',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 30 },
        { duration: '1m', target: 100 },
        { duration: '2m', target: 100 },
        { duration: '30s', target: 0 },
      ],
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<2000'],
    errors: ['rate<0.05'],
  },
};

const counties = ['Nairobi', 'Mombasa', 'Kiambu', 'Nakuru', 'Kajiado', 'Kisumu'];
let cachedPropertyIds = [];

export function setup() {
  const res = http.get(`${BASE_URL}/api/properties/search?size=20`);
  const ids = [];
  if (res.status === 200) {
    try {
      const body = JSON.parse(res.body);
      (body.content || []).forEach(p => ids.push(p.id));
    } catch (e) {}
  }
  return { propertyIds: ids };
}

export function searchProperties() {
  const county = counties[Math.floor(Math.random() * counties.length)];
  const res = http.get(`${BASE_URL}/api/properties/search?county=${county}&page=0&size=12`);
  searchDuration.add(res.timings.duration);
  const ok = check(res, {
    'search status is 200': r => r.status === 200,
    'search responds under 2s': r => r.timings.duration < 2000,
  });
  errorRate.add(!ok);
  sleep(Math.random() * 2 + 1);
}

export function viewPropertyDetail(data) {
  if (!data.propertyIds || data.propertyIds.length === 0) {
    sleep(1);
    return;
  }
  const id = data.propertyIds[Math.floor(Math.random() * data.propertyIds.length)];
  const res = http.get(`${BASE_URL}/api/properties/${id}`);
  detailDuration.add(res.timings.duration);
  const ok = check(res, {
    'detail status is 200': r => r.status === 200,
    'detail responds under 2s': r => r.timings.duration < 2000,
  });
  errorRate.add(!ok);
  sleep(Math.random() * 3 + 1);
}
