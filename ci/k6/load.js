// Short, modest load test run against the real service in CI.
//
// The goal is evidence, not benchmarking: it shows the endpoints answer correctly under
// concurrent load and publishes latency percentiles next to the recorded traffic.
import http from 'k6/http';
import { check, group, sleep } from 'k6';

const BASE = __ENV.BASE_URL || 'http://localhost:8080';
const CUSTOMER_ID = '11111111-1111-4111-8111-111111111111';
const PRODUCT_ID = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa';

export const options = {
  scenarios: {
    browse: {
      executor: 'ramping-vus',
      startVUs: 1,
      stages: [
        { duration: '10s', target: 5 },
        { duration: '20s', target: 10 },
        { duration: '10s', target: 0 },
      ],
      gracefulRampDown: '5s',
    },
  },
  thresholds: {
    // Generous but meaningful: a regression to hundreds of milliseconds fails the build.
    http_req_duration: ['p(95)<500', 'p(99)<900'],
    http_req_failed: ['rate<0.01'],
    checks: ['rate>0.99'],
  },
};

export default function () {
  group('read paths', () => {
    const orders = http.get(
      `${BASE}/api/orders?customerId=${CUSTOMER_ID}&size=5`,
      { tags: { endpoint: 'orders' } },
    );
    check(orders, {
      'orders 200': (r) => r.status === 200,
      'orders has items': (r) => r.json('items') !== undefined,
    });

    const products = http.get(`${BASE}/api/products?query=keyboard`, {
      tags: { endpoint: 'products' },
    });
    check(products, { 'products 200': (r) => r.status === 200 });
  });

  group('graphql', () => {
    const payload = JSON.stringify({
      query: `query($id: ID!) { orders(customerId: $id, size: 5) { totalCount items { id totalCents } } }`,
      variables: { id: CUSTOMER_ID },
    });
    const res = http.post(`${BASE}/graphql`, payload, {
      headers: { 'Content-Type': 'application/json' },
      tags: { endpoint: 'graphql' },
    });
    check(res, {
      'graphql 200': (r) => r.status === 200,
      'graphql no errors': (r) => r.json('errors') === undefined || r.json('errors') === null,
    });
  });

  group('write path with idempotency', () => {
    // A unique key per iteration: this measures real inserts, not replays.
    const key = `k6-${__VU}-${__ITER}-${Date.now()}`;
    const res = http.post(
      `${BASE}/api/orders`,
      JSON.stringify({
        customerId: CUSTOMER_ID,
        items: [{ productId: PRODUCT_ID, quantity: 1 }],
      }),
      {
        headers: { 'Content-Type': 'application/json', 'Idempotency-Key': key },
        tags: { endpoint: 'create-order' },
      },
    );
    check(res, { 'order created': (r) => r.status === 201 });
  });

  sleep(0.2);
}

export function handleSummary(data) {
  const path = __ENV.SUMMARY_PATH || 'dist/data/k6-summary.json';
  const metrics = data.metrics || {};
  const p95 = metrics.http_req_duration ? metrics.http_req_duration.values['p(95)'] : null;
  const rps = metrics.http_reqs ? metrics.http_reqs.values.rate : null;
  const failures = metrics.http_req_failed ? metrics.http_req_failed.values.rate : null;

  console.log(
    `k6: p95=${p95 === null ? 'n/a' : p95.toFixed(1)}ms rps=${rps === null ? 'n/a' : rps.toFixed(1)} failures=${failures === null ? 'n/a' : (failures * 100).toFixed(2) + '%'}`,
  );

  return {
    [path]: JSON.stringify(data, null, 2),
  };
}
