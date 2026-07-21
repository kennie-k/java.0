# Load Tests

Requires [k6](https://k6.io/docs/get-started/installation/).

## Property search & detail load test

Ramps to 200 concurrent virtual users against the cached public search and detail endpoints.

```
k6 run load-tests/property-search-load-test.js
k6 run -e BASE_URL=https://staging.smartre.co.ke load-tests/property-search-load-test.js
```

Pass thresholds: p95 latency under 2s, error rate under 5%.

## Gateway rate limiter verification

Fires 150 requests/second at a single endpoint for 10 seconds to confirm the documented
50 req/s (burst 100) Redis rate limiter actually rejects excess traffic with 429s.

```
k6 run load-tests/rate-limiter-verification.js
k6 run -e TEST_TOKEN=<jwt> load-tests/rate-limiter-verification.js
```

If the summary reports zero 429s under this load, the rate limiter is not enforcing and
needs investigation before relying on the "50 req/s per user" scaling claim.
