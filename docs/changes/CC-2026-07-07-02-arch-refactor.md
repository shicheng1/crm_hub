# CC-2026-07-07-02 架构解耦重构（搭骨架）

- 状态：planning
- 关联技术债：§7 A1 / A2 / A3
- 创建：2026-07-07
- 修改人：AI（待用户确认范围后执行）

## 背景
`OrderServiceImpl` 585 行上帝类：审批引擎逻辑（推进/驳回/跳过/权限/快照解析/状态流转）与工单 CRUD、缓存失效、通知、MQ、分布式锁全部缠在一个类里。
- `orderCacheService.evictOrder` + `dashboardService.evictStatsCache` 散落 4 处（handleApproved/handleRejected/resubmitOrder）。
- `sendNotifications` 在锁内同步调 WebSocket + MQ。
- `OrderServiceImpl` 反向注入 `DashboardService` 去失效看板缓存（工单领域感知看板存在）。
- 项目零事件机制，缓存失效/通知均硬编码调用。

后续还有很多改动，先把高内聚低耦合的骨架立起来，让审批逻辑、缓存、通知各自有归属。

## 范围（分阶段）
- **阶段一（本次执行）**：A1 拆审批引擎 + A3 事件解耦缓存与通知。建立领域事件机制，去掉反向依赖，通知移出锁（顺带修 P0-3）。
- **阶段二（后续单独 plan）**：A2 锁模板化。把 `tryLock/finally releaseLock` 抽成 `DistributedLockTemplate` 或 `@DistributedLock` 注解 + AOP，锁释放绑定事务提交（`TransactionSynchronization`）。因涉及事务边界，单独排期，不在本次。

## 影响范围
- 新增包：`event/`、`approval/engine/`
- 新增文件：
  - `event/OrderApprovalEvent.java`（事件载体）
  - `approval/engine/ApprovalEngineService.java`（审批引擎，迁入私有方法）
  - `event/CacheEvictListener.java`（`@EventListener` 失效工单+看板缓存）
  - `event/NotificationListener.java`（`@EventListener @Async` 发通知+MQ）
- 修改文件：
  - `OrderServiceImpl.java`：删 `DashboardService` 依赖与 `evictStatsCache` 调用；审批逻辑改为调 `ApprovalEngineService` 拿到 `ApprovalOutcome` 后写库+发事件；`sendNotifications` 删除（交由监听器）
  - `DashboardServiceImpl.java`：去掉被反向依赖，改为由 `CacheEvictListener` 调用其 `evictStatsCache`（方法保留）
  - 启动类加 `@EnableAsync`（通知异步）
- 不触碰：DB schema、前端、任何红线项（无删除/无密钥/无发布）

## 方案（阶段一）

### 1. 审批引擎 `ApprovalEngineService`
迁入原 `OrderServiceImpl` 全部审批流程私有方法：
`createFlowSnapshot` `getFlowSnapshot` `getSnapshotSteps` `loadRuntimeStepsWithApprovers`
`isStepApprover` `hasApproved` `isAllApproversApproved` `getRejectMode`
`getStep` `getNextStep` `getPrevStep` `skipDuplicateApprovers` `isMyTurn` `transitStatus`。
依赖注入：`stepMapper` `stepApproverMapper` `recordMapper` `flowMapper` `flowSnapshotMapper` `orderMapper` `objectMapper` `ApprovalModeEvaluator` `OrderStatusTransition`。

对外暴露两个编排方法（**只算状态，不写库/不清缓存/不发通知**）：
- `ApprovalOutcome approve(WorkOrder order, ApprovalFlowStep step, LoginUser user)` → 返回目标状态、目标步骤、是否完成、日志操作、日志详情。
- `ApprovalOutcome reject(WorkOrder order, ApprovalFlowStep step, LoginUser user, String remark)` → 同上（按 RESTART/PREVIOUS/ORIGIN 计算）。

`ApprovalOutcome` 为新建 DTO：`{int targetStatus; Integer targetStep; boolean completed; String logOp; String logDetail;}`。

### 2. 领域事件 `OrderApprovalEvent`
字段：`orderId, title, creatorId, creatorName, operatorId, operatorName, approved(boolean), remark, approveTime`。
`OrderServiceImpl` 在审批事务提交后 `applicationEventPublisher.publishEvent(...)`。

### 3. `OrderServiceImpl.approveOrder` 改造
- 保留手写锁（阶段二再模板化）。
- 锁内：查 order → 校验步骤/权限/重复 → 写 `ApprovalRecord` → 调 `engine.approve/reject` 得 `outcome` → `orderMapper.updateById` 按 outcome 落库 → `saveLog`。
- 删：`dashboardService.evictStatsCache()`、`orderCacheService.evictOrder()`、`sendNotifications(...)`。
- 事务提交后：`publishEvent(new OrderApprovalEvent(...))`（删除 `DashboardService` 字段与 import）。

### 4. 监听器（解耦缓存/通知）
- `CacheEvictListener.onOrderApproved(OrderApprovalEvent)`：`orderCacheService.evictOrder(orderId)` + `dashboardService.evictStatsCache()`。
- `NotificationListener.onOrderApproved(OrderApprovalEvent) @Async`：组装 `ApprovalMessage` → `notificationService.notifyCreator(msg)` + `approvalProducerProvider.getIfAvailable().sendApprovalNotify(msg)`。
- 通知移出锁 → 修 P0-3（锁内只剩 DB 写，快）。

## 验证
```bash
cd backend && mvn -q test
# 手验：审批通过/驳回(RESTART/PREVIOUS/ORIGIN)/相同审批人跳过/会签
# 确认：缓存失效、WebSocket 通知仍到达、无双审
# 性能复测
BASE_URL=http://localhost:8080 USERNAME=admin PASSWORD=123456 k6 run docs/performance/load-test.js
```
- 预期：功能不变；`OrderServiceImpl` 不再依赖 `DashboardService`；审批后通知异步到达；单测全过。

## 回滚
- 按「新增包 / 改 OrderServiceImpl / 改 DashboardServiceImpl / 启动类」分别 commit，`git revert` 互不牵连。
- 阶段二（锁模板化）独立 plan，不混入本次。
