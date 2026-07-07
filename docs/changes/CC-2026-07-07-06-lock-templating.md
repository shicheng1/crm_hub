# CC-2026-07-07-06 锁模板化（A2 阶段二）

- 状态：planning
- 关联技术债：§7 A2（横切关注点未隔离）/ P0-3（锁释放时机早于提交）
- 创建：2026-07-07
- 修改人：AI

## 背景
锁/缓存/通知/MQ 等横切逻辑硬编码在业务方法体内（A2）。其中分布式锁使用尤为重复：`OrderServiceImpl` 的 `createOrder`（:66-92）与 `approveOrder`（:201-280）各自写一遍 `tryLock → try {…} finally { releaseLock }` 样板代码；且 `finally` 在 `@Transactional` 方法返回后即释放，**早于事务提交**（P0-3 残留风险）：锁释放后、提交前，并发线程可抢到锁读到未提交的旧状态，存在「并发双审 / 读到脏状态」隐患。通知/MQ/缓存已在 CC-02 解耦为领域事件，本次只收口「锁」这一横切点。

## 影响范围
- 新增文件：`backend/src/main/java/com/zmd/order/lock/LockTemplate.java`
- 修改：`backend/src/main/java/com/zmd/order/service/impl/OrderServiceImpl.java`（注入 `LockTemplate`，移除对 `RedisDistributedLock` 的直接依赖；重构 `createOrder`/`approveOrder` 两处锁样板）
- `RedisDistributedLock.java`：保持不变（P2-2 忙等 50ms 可接受），仅改由 `LockTemplate` 调用
- 接口 / DB：无变更；不触碰 Flyway
- 红线：否（无依赖安装 / 配置 / DB 变更 / push）

## 方案
1. 新增 `LockTemplate`（`@Component`，持有 `RedisDistributedLock`），提供两个重载：
   - `void executeWithLock(key, waitSec, leaseSec, lockFailedMsg, Runnable)`
   - `<T> T executeWithLock(key, waitSec, leaseSec, lockFailedMsg, Supplier<T>)`
   - 核心逻辑：`tryLock` 失败抛 `429`（沿用各调用点原提示文案）；成功则执行业务；**若当前处于 Spring 事务中，注册 `TransactionSynchronization.afterCompletion` 在事务提交/回滚后才释放锁；否则立即释放**；业务抛异常时立即释放（防泄漏）。彻底消除「锁释放早于提交」。
2. `OrderServiceImpl`：
   - 删除 `private final RedisDistributedLock redisLock;` 字段与 import；改为注入 `LockTemplate lockTemplate`。
   - `createOrder`：整个方法体包进 `lockTemplate.executeWithLock(LOCK_ORDER_CREATE + userId, 5, 30, "操作过于频繁，请稍后再试", () -> { …; return order.getId(); })`，删 `try/finally`。
   - `approveOrder`：同理包进 `lockTemplate.executeWithLock(LOCK_ORDER_APPROVE + orderId, 5, 60, "该工单正在审批中，请稍后再试", () -> { … })`。
3. 行为保持：锁 key 前缀、wait/lease 时长、失败文案、异常语义全部不变；仅结构收敛 + 释放时机修正（正确性增强，非行为变更）。

## 验证
- 命令（用户在 Java 8 环境）：`cd backend && mvn -q test`
- 接口手验：并发审批同一工单只应成功一次（其余 429）；创建/审批正常。
- 预期：单测通过；`grep -rn "redisLock\." OrderServiceImpl.java` 无残留；锁释放日志出现在事务提交之后。

## 回滚
- 删除 `LockTemplate.java`，`OrderServiceImpl` 还原为 `try/finally` 直调 `RedisDistributedLock`。
