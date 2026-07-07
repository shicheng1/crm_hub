# CC-2026-07-07-01 P0 性能修复

- 状态：in-progress
- 关联技术债：§7 P0-1 / P0-2 / P0-3
- 创建：2026-07-07
- 修改人：AI（待用户确认后执行）

## 背景
系统整体架构不差，但 README 宣称的"消除 N+1""缓存优化"在真实列表/看板路径被抵消：
- 看板一进页面打 22 次 `SELECT COUNT(*)`；
- 待办/已办列表每条工单仍逐条查快照+审批记录+创建人（N+1 回归）；
- 分布式锁 lease 仅 60s，锁内同步做 WebSocket/MQ/多表写，`finally` 释放早于事务提交，存在并发双审与读到未提交状态风险。

三个都是 P0 级真实问题，按性价比排序修复。

## 影响范围
- 文件：`DashboardServiceImpl`、`OrderServiceImpl`、`RedisDistributedLock`、`OrderCacheService`（可能）
- 接口：`/api/dashboard/stats`、`/api/dashboard/trend`、`/api/order/todo`、`/api/order/done`、`/api/order/approve`
- 是否触碰红线：否（不改 schema、不删文件、不发布）

## 方案

### 1. 看板合并查询（P0-1，改动最小收益最大）
- `getStats`：8 次 `COUNT(*)` → 1 条
  `SELECT status, COUNT(*) c FROM work_order GROUP BY status`，内存映射成各状态计数。
- `getTrend`：14 次 `COUNT(*)` → 2 条
  - 创建趋势：`SELECT DATE(create_time) d, COUNT(*) c FROM work_order WHERE create_time >= ? GROUP BY DATE(create_time)`
  - 通过趋势：同上对 `approve_time`。
- 22 次 → 3 次。缓存逻辑保持不变（仍 5 分钟，审批后失效）。

### 2. 列表 enrich 批量化（P0-2）
- todo/done 先用分页拿到 `orderId` 列表；
- 用 `IN (...)` 批量查 `approval_record`、`user`、`order_flow_snapshot`；
- 一次循环内填充，消灭逐条 N+1；
- `WorkOrder` 增加瞬态 `enriched` 标记，缓存命中且已 enrich 直接返回不 re-enrich（顺带修 P1-1）。

### 3. 锁与通知解耦（P0-3）
- `sendNotifications`（WebSocket/MQ）移到**锁释放之后**，最好事务提交后异步（Spring `TransactionSynchronization` 或 `@Async`）；
- 锁释放改到事务真正提交之后，避免下一持有者读到未提交状态；
- 若单笔审批可能超 60s，评估调大 lease 或把重活彻底移出锁（当前规模优先解耦通知即可）。

## 验证
```bash
cd backend && mvn -q test
# 手验看板
curl -s localhost:8080/api/dashboard/stats -H "Authorization: Bearer $TOKEN"
curl -s "localhost:8080/api/dashboard/trend?days=7" -H "Authorization: Bearer $TOKEN"
# 手验列表无 N+1：开启 MySQL general log 或加慢 SQL 拦截观察一页查询数
# 性能复测
BASE_URL=http://localhost:8080 USERNAME=admin PASSWORD=123456 k6 run docs/performance/load-test.js
```
- 预期：看板 SQL 次数 22→3；列表一页查询数显著下降；并发审批不出现双审；单测全过。

## 回滚
- 每个子项为独立 commit；任一项异常 `git revert` 对应 commit 即可，互不牵连。
- 锁改动风险最高，先单独小步验证再合并。

## 执行记录（2026-07-07）

- 状态：`in-progress`（代码落地，待 Java 8 环境 `mvn test` + 手验）
- **P0-1 看板合并**：新增 `OrderMapper.selectStatsSummary`（单条条件聚合，CAST AS UNSIGNED 保证映射 Long）/ `selectCreateTrend` / `selectApproveTrend`（按日期分组）；`DashboardServiceImpl.getStats`/`getTrend` 改写，22 次 `COUNT` → 3 次（状态分布 1 条 + 创建趋势 1 条 + 通过趋势 1 条）。缓存逻辑（5 分钟、审批后失效）不变。
- **P0-2 列表 enrich 批量化**：新增 `OrderServiceImpl.enrichOrders(List)`，`todoList`/`doneList` 改调；`WorkOrder.enriched` 瞬态标记 + `enrichOrder` guard 修复 P1-1（详情接口改为先 enrich 再 `cacheOrder`，缓存命中直接返回不 re-enrich）；`ApprovalEngineService.getFlowSnapshots(List)` 批量查快照。一页查询从 ~30 次降到 4 次（工单分页 1 + 记录 IN 1 + 创建人 IN 1 + 快照 IN 1）。顺带 P1-3：三处列表 `size` 加 `MAX_PAGE_SIZE=100` 上限防 DoS。
- **P0-3 锁内通知**：已由 [CC-2026-07-07-02](docs/changes/CC-2026-07-07-02-arch-refactor.md) 阶段一完成（通知移出锁、`@Async` 事务提交后执行）；锁释放时机 / lease 归 A2（阶段二锁模板化，单独 plan）。
- **未验证**：本地为 JDK21 + Maven 损坏，无法编译。需在 Java 8 环境跑 `mvn test` 并手验：看板 SQL 次数 22→3、列表一页查询显著下降、详情缓存命中不再 re-enrich、审批链路（通过/驳回三种模式/WebSocket 通知/看板缓存失效）正常。
