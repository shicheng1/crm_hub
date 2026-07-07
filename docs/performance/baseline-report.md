# 性能基准报告（Baseline Report）

> 本文件记录 zmd-crm 接口层的压测方案与基准数据。
> 下面的「基准数据」为**模板/参考目标**，需在目标环境实际跑一遍后填入真实数值（见文末表格）。
> 未经过真实压测前，不要把这些数字当作已验证结论。

## 1. 压测目标

验证 P4 阶段的可观测性与性能优化在真实负载下是否成立：

- 缓存命中率（`/api/dashboard/cache-stats`）在重复读场景下应明显 > 0，证明 `OrderCacheService` 的缓存生效。
- 关键读接口（看板统计、工单列表）在 50 VU 下 P95 < 500ms。
- 错误率 < 1%。
- 索引优化（V6）后，`work_order` 按 `status+create_time`、`approval_record` 按 `order_id` 的查询应走索引（`EXPLAIN` 验证见 `docs/database.md` 第 6 节）。

## 2. 测试环境（填写你的实际环境）

| 项 | 值 |
|----|----|
| 后端 | Spring Boot 2.7 / Java 8 |
| 部署方式 | 单机 `mvn spring-boot:run` / Docker |
| CPU | _待填_ |
| 内存 | _待填_ |
| MySQL | _待填（版本 / 配置）_ |
| Redis | _待填_ |
| 压测机 | 与后端同机 / 独立机 |

## 3. 接口清单

| 接口 | 方法 | 说明 | 鉴权 |
|------|------|------|------|
| `/api/auth/login` | POST | 登录（仅测试开始时调用 1 次） | 否 |
| `/api/dashboard/stats` | GET | 看板统计（带 Redis 缓存） | 是 |
| `/api/order/page` | GET | 工单分页列表 | 是 |
| `/api/order/create` | POST | 创建工单 | 是 |

## 4. 运行方式

### k6（推荐）

```bash
BASE_URL=http://localhost:8080 USERNAME=admin PASSWORD=123456 \
  k6 run docs/performance/load-test.js
```

阶梯加压：0 → 20 VU（30s）→ 50 VU（1m）→ 0（30s）。可加 `--vus 100 --duration 3m` 自定义。

### JMeter

```bash
jmeter -n -t docs/performance/jmeter-test-plan.jmx -l result.jtl
jmeter -g result.jtl -o report-html
```

## 5. 观测指标

压测过程中同步观察：

1. **应用层**：后端日志中的慢 SQL 告警（`SlowSqlInterceptor`，阈值 500ms，生产建议 1000ms）。
2. **缓存层**：`GET /api/dashboard/cache-stats` 返回的 `hitRate` / `hitRatePercent`。
3. **链路层**：响应头 `X-Trace-Id`，用于关联全链路日志。
4. **资源层**：MySQL `EXPLAIN`、Redis `INFO stats`、机器 CPU/内存。

## 6. 参考目标（非实测结论）

| 场景 | VU | 预期 TPS | P95 响应时间 | 错误率 | 缓存命中率 |
|------|----|---------|-------------|--------|-----------|
| 看板+列表混合读 | 50 | ≥ 300 | < 500ms | < 1% | 读多写少时 > 80% |
| 含创建工单写 | 50 | ≥ 150 | < 800ms | < 1% | 视缓存填充情况 |
| 峰值 100 VU | 100 | ≥ 400 | < 1000ms | < 2% | — |

> 说明：以上为同机开发环境的经验量级参考。具体数值取决于机器配置、MySQL/Redis 是否独立部署、数据量大小。请实测后回填下表。

## 7. 实测结果（回填区）

| 场景 | VU | 实测 TPS | P95 | 错误率 | 缓存命中率 | 备注 |
|------|----|---------|-----|--------|-----------|------|
| _场景1_ | _50_ | _待填_ | _待填_ | _待填_ | _待填_ | _待填_ |
| _场景2_ | _100_ | _待填_ | _待填_ | _待填_ | _待填_ | _待填_ |

## 8. 已知风险与调优方向

- **登录限流**：`/api/auth/login` 限流 5 次/分钟，压测时务必只登录一次复用 token；单独压登录需临时放开。
- **看板缓存 5 分钟过期**：`/api/dashboard/stats` 在缓存失效瞬间会回源 MySQL，瞬时压力上升，可用 `EXPLAIN` 确认已走 `idx_status_create`。
- **慢 SQL 阈值**：开发环境 500ms，生产建议 1000ms，避免误报。阈值见 `application-*.yml` 的 `slow-sql-threshold-ms` 配置（如未配置则默认 500ms）。
