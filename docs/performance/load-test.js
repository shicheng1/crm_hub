// zmd-crm 接口压测脚本（k6）
//
// 为什么用 k6：比 JMeter 更轻量，单二进制 + Node 生态即可运行，
// 适合在 CI / 开发机快速跑出基准数据。JMeter 版本见同目录 jmeter-test-plan.jmx。
//
// 运行：
//   npm install -g k6            # 或 https://k6.io/docs/get-started/installation/
//   k6 run load-test.js                      # 默认 20→50 VU，约 2 分钟
//   BASE_URL=http://localhost:8080 USERNAME=admin PASSWORD=123456 k6 run load-test.js
//   k6 run --vus 100 --duration 3m load-test.js   # 自定义规模
//
// 关键设计：登录只在 setup() 执行一次（全局 1 次），避免触发后端
// /api/auth/login 的限流（@RateLimit auth:login 5次/分钟）。token 通过
// data 传给每个 VU 复用。

import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE = __ENV.BASE_URL || 'http://localhost:8080';
const USERNAME = __ENV.USERNAME || 'admin';
const PASSWORD = __ENV.PASSWORD || '123456';

export const options = {
  // 阶梯加压：先升到 20 VU，保持 50 VU，再回收
  scenarios: {
    ramp: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 20 },
        { duration: '1m', target: 50 },
        { duration: '30s', target: 0 },
      ],
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'], // 错误率 < 1%
    http_req_duration: ['p(95)<500'], // 95% 请求 < 500ms
  },
};

// 全局仅执行一次：登录拿 token
export function setup() {
  const res = http.post(
    `${BASE}/api/auth/login`,
    JSON.stringify({ username: USERNAME, password: PASSWORD }),
    { headers: { 'Content-Type': 'application/json' } }
  );
  check(res, { 'login status 200': (r) => r.status === 200 });
  const token = res.json('data.token');
  if (!token) {
    throw new Error('登录失败，未返回 token（检查账号/密码/限流）');
  }
  return { token };
}

export default function (data) {
  const headers = {
    'Content-Type': 'application/json',
    Authorization: `Bearer ${data.token}`,
  };

  // 1. 看板统计
  let r = http.get(`${BASE}/api/dashboard/stats`, { headers });
  check(r, { 'stats 200': (x) => x.status === 200 });

  // 2. 工单分页列表
  r = http.get(`${BASE}/api/order/page?current=1&size=20`, { headers });
  check(r, { 'order page 200': (x) => x.status === 200 });

  // 3. 创建工单
  const createBody = JSON.stringify({
    title: `压测工单-${Math.floor(Math.random() * 1e9)}`,
    content: 'k6 自动创建',
    flowId: 1,
  });
  r = http.post(`${BASE}/api/order/create`, createBody, { headers });
  check(r, { 'create 200': (x) => x.status === 200 });

  sleep(1);
}

export function teardown(data) {
  http.post(`${BASE}/api/auth/logout`, '', {
    headers: { Authorization: `Bearer ${data.token}` },
  });
}
