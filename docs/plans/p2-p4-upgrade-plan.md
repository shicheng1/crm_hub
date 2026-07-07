# zmd-crm P2-P4 升级计划书

> **前置条件**：P0（测试基础）、P1（Docker/部署/Actuator）、P5（文档）已完成。
> 当前基线：13 个测试通过，mvn test + npm build 通过，分支 `feature/production-upgrade`。

---

## 当前代码现状摘要

| 模块 | 已有 | 缺失 |
|------|------|------|
| 认证 | JWT + Redis token 主动失效 | 无登录失败锁定、无 refresh token、401/403 返回 HTTP 200 |
| 权限 | LoginUser ThreadLocal 存 role | 无接口级权限控制，UserController 全裸暴露 |
| 密码 | BCryptPasswordEncoder | init.sql 初始密码明文 `123456` |
| 审计 | order_operation_log（工单操作） | 无登录/登出/配置变更审计 |
| 审批流 | 多级、多审批人任一通过、3种驳回策略 | 无会签(ALL)、无版本快照、状态流转散落 |
| 可观测性 | Actuator 已引入 | 无 traceId、无慢SQL日志、无缓存命中率 |
| 性能 | Redis缓存+空值缓存+分布式锁 | 无压测脚本、前端 element-plus 1MB+ |

---

## P2：安全与权限（7 个任务）

### Task P2-1：登录失败锁定
**目标**：连续失败5次锁定账号15分钟

**文件**：
- 修改：`auth/AuthController.java`
- 修改：`common/Constants.java`（加 key 前缀）
- 新增：`auth/LoginAttemptService.java`

**实现**：
- Redis key: `order:login:fail:{username}`，TTL 15min
- 每次失败 INCR，达到5次设置锁定标记 `order:login:lock:{username}`
- 登录时先检查锁定状态
- 成功登录清除计数

**验证**：新增 `LoginAttemptServiceTest.java`，mock RedisTemplate 验证计数和锁定逻辑

---

### Task P2-2：接口级权限控制
**目标**：用户管理仅 ADMIN，审批流管理仅 ADMIN，工单操作需登录

**文件**：
- 新增：`auth/RequireRole.java`（注解）
- 修改：`auth/AuthInterceptor.java`（检查注解）
- 修改：`controller/UserController.java`（加 `@RequireRole("ADMIN")`）
- 修改：`controller/ApprovalFlowController.java`（加 `@RequireRole("ADMIN")`）

**实现**：
- 自定义注解 `@RequireRole("ADMIN")` 标在 Controller 方法或类上
- AuthInterceptor 解析注解，对比 LoginUser.role
- 权限不足返回 403

**验证**：新增 `RequireRoleInterceptorTest.java`

---

### Task P2-3：统一 HTTP 状态码
**目标**：认证失败返回 401，权限不足返回 403，而不是 HTTP 200 + body code

**文件**：
- 修改：`auth/AuthInterceptor.java`
- 修改：`common/GlobalExceptionHandler.java`

**实现**：
- AuthInterceptor `writeError` 设置 `response.setStatus(401/403)` 而非 200
- 前端 `request.js` 的拦截器增加 401/403 处理（跳转登录页）

**验证**：手动 curl 验证返回码

---

### Task P2-4：初始化密码 BCrypt 化
**目标**：init 数据不再保留明文密码（已落地于 `db/migration/V1__init_schema.sql`，BCrypt hash 直写种子用户，原 `V2__bcrypt_passwords.sql` 合并进 V1 后删除）

**实现**：
- 用 BCrypt 编码 `123456` 的 hash 替换明文
- AuthController 的 `matchPassword` 兼容逻辑可保留（过渡期），但 init 数据已加密

**验证**：启动后登录验证

---

### Task P2-5：审计日志
**目标**：记录登录、登出、用户管理、审批流变更操作

**文件**：
- 新增：`entity/AuditLog.java`
- 新增：`mapper/AuditLogMapper.java`
- 新增：`service/AuditLogService.java` + `impl/AuditLogServiceImpl.java`
- 新增：`db/migration/V4__audit_log.sql`
- 修改：`auth/AuthController.java`（登录/登出记录）
- 修改：`controller/UserController.java`（用户管理记录）
- 修改：`controller/ApprovalFlowController.java`（审批流变更记录）

**实现**：
- AOP 或手动调用，记录：操作人、操作类型、目标、IP、时间
- 表结构：id, operator_id, operator_name, action, target_type, target_id, ip, detail, create_time

**验证**：操作后查表确认记录存在

---

### Task P2-6：Refresh Token
**目标**：access token 过期前用 refresh token 换新 token

**文件**：
- 修改：`auth/JwtUtil.java`（生成 refresh token，7天过期）
- 修改：`auth/AuthController.java`（登录返回双 token，新增 /auth/refresh）
- 修改：`common/Constants.java`（refresh token 前缀）
- 修改：`frontend/src/api/auth.js`
- 修改：`frontend/src/utils/request.js`（401 时自动 refresh）

**实现**：
- 登录返回 `accessToken`（24h）+ `refreshToken`（7d）
- Redis 存 `order:token:refresh:{refreshToken}` -> userId
- `/auth/refresh` 接口：验 refresh token → 发新 access token
- 前端 401 拦截器：用 refresh token 换新 access token 后重试请求

**验证**：手动测试 token 过期后自动续期

---

## P3：审批流引擎深化（4 个任务）

### Task P3-1：会签/或签模式
**目标**：步骤级别支持 ALL（会签，全部通过才推进）和 ANY（或签，任一通过即推进）

**文件**：
- 修改：`entity/ApprovalFlowStep.java`（加 `approveMode` 字段）
- 修改：`service/impl/OrderServiceImpl.java`（handleApproved 逻辑分支）
- 新增：`db/migration/V4__add_approve_mode.sql`

**实现**：
- `ApprovalFlowStep` 加 `approveMode` 字段（`ANY`/`ALL`，默认 `ANY` 保持兼容）
- handleApproved 中：
  - ANY：当前逻辑不变（任一通过即推进）
  - ALL：检查当前步骤所有审批人是否都已通过，全通过才推进
- 前端 FlowManage.vue 加模式选择

**验证**：新增 `ApprovalModeTest.java`

---

### Task P3-2：审批流版本快照
**目标**：工单创建时绑定流程快照，流程后续修改不影响历史工单

**文件**：
- 新增：`entity/OrderFlowSnapshot.java`
- 新增：`mapper/OrderFlowSnapshotMapper.java`
- 修改：`service/impl/OrderServiceImpl.java`（创建工单时保存快照，审批时读快照）
- 新增：`db/migration/V5__flow_snapshot.sql`

**实现**：
- 创建工单时，将当前 flow + steps + approvers 序列化为 JSON 存入 `order_flow_snapshot` 表
- 审批逻辑从快照读取步骤和审批人，不再实时查 `approval_flow_step`
- 前端展示也读快照

**验证**：修改审批流后，历史工单审批流程不变

---

### Task P3-3：状态机化
**目标**：将工单状态迁移集中管理，防止散落在 service 各方法中

**文件**：
- 新增：`statemachine/OrderStatus.java`（枚举）
- 新增：`statemachine/OrderStatusTransition.java`（状态迁移规则）
- 修改：`service/impl/OrderServiceImpl.java`（用状态机校验迁移合法性）

**实现**：
- 枚举所有状态：PENDING, REVIEWING, APPROVED, REJECTED, CLOSED, RETURNED
- 定义合法迁移：PENDING→REVIEWING, REVIEWING→APPROVED, REVIEWING→REJECTED, REVIEWING→RETURNED, RETURNED→REVIEWING, ...
- 每次状态变更前校验迁移合法性，非法迁移抛 BusinessException

**验证**：新增 `OrderStatusTransitionTest.java`

---

### Task P3-4：超时自动催办
**目标**：工单在某步骤停留超过 N 小时，自动通知审批人

**文件**：
- 修改：`schedule/OrderScheduleTask.java`
- 修改：`websocket/NotificationService.java`

**实现**：
- 定时任务每天扫描状态为 REVIEWING 且 update_time 超过 24h 的工单
- 通过 WebSocket 推送催办通知给当前步骤审批人
- 记录催办日志

**验证**：手动修改工单 update_time 验证触发

---

## P4：性能与可观测性（5 个任务）

### Task P4-1：日志 traceId 链路追踪
**目标**：每个请求分配唯一 traceId，全链路日志可关联

**文件**：
- 新增：`config/TraceIdFilter.java`（Servlet Filter）
- 修改：`backend/src/main/resources/logback-spring.xml`（日志格式加 traceId）
- 修改：`frontend/src/utils/request.js`（请求头带 traceId）

**实现**：
- Filter 中 `MDC.put("traceId", UUID)` 
- 响应头 `X-Trace-Id` 返回
- logback pattern: `%d [%X{traceId}] %-5level %logger - %msg%n`

**验证**：查看日志确认 traceId 贯穿

---

### Task P4-2：慢 SQL 日志拦截器
**目标**：执行超过阈值的 SQL 自动记录告警日志

**文件**：
- 修改：`backend/src/main/resources/application-dev.yml`（MyBatis-Plus 配置）
- 新增：`config/SlowSqlInterceptor.java`（MyBatis Interceptor）

**实现**：
- 自定义 MyBatis Interceptor，记录执行时间超过 500ms 的 SQL
- 日志格式：`WARN 慢SQL: {}ms, sql={}`

**验证**：构造慢查询验证日志输出

---

### Task P4-3：缓存命中率统计
**目标**：记录缓存命中/未命中次数，通过 Actuator 暴露

**文件**：
- 修改：`cache/OrderCacheService.java`
- 修改：`service/impl/DashboardServiceImpl.java`
- 新增：`config/CacheMetrics.java`

**实现**：
- AtomicLong 计数器：hit / miss
- 通过 Actuator `/actuator/metrics` 或自定义 endpoint 暴露
- 或在 DashboardController 加 `/api/dashboard/cache-stats` 接口

**验证**：多次请求后查看命中率

---

### Task P4-4：数据库索引优化
**目标**：补全索引，记录 explain 分析

**文件**：
- 新增：`db/migration/V7__add_indexes.sql`
- 修改：`docs/database.md`（补充索引说明）

**实现**：
- 补全：work_order.flow_id、approval_record.order_id+step_id、approval_step_approver.step_id+user_id
- 索引设计文档化

**验证**：`EXPLAIN` 验证索引命中

---

### Task P4-5：JMeter 压测脚本
**目标**：提供可复现的压测脚本和基准报告

**文件**：
- 新增：`docs/performance/jmeter-test-plan.jmx`
- 新增：`docs/performance/baseline-report.md`

**实现**：
- JMeter 脚本：登录、创建工单、查询列表、审批
- 基准报告：TPS、响应时间、错误率
- README 中说明如何运行

**验证**：执行压测脚本生成报告

---

## 执行策略

1. **按 P2 → P3 → P4 顺序推进**，每个 Task 完成后验证 `mvn test` + `npm build` 通过
2. **每个 Task 完成后 git commit**，保持提交粒度清晰
3. **P2 优先级最高**，安全是面试必问项
4. **P3 是核心业务亮点**，会签和版本快照是加分项
5. **P4 可观测性**是区分"能开发"和"能上线"的关键

## 预期产出

| 阶段 | 新增文件 | 修改文件 | 新增测试 | 预计提交数 |
|------|---------|---------|---------|-----------|
| P2 | ~8 | ~10 | ~3 | 6 |
| P3 | ~5 | ~4 | ~2 | 4 |
| P4 | ~5 | ~5 | ~1 | 5 |
| **合计** | **~18** | **~19** | **~6** | **~15** |

## 风险与应对

| 风险 | 应对 |
|------|------|
| P3-2 快照方案增加复杂度 | 先做 P3-1 会签，快照作为独立可选任务 |
| Refresh token 前端改造可能引入 bug | 保留旧 token 逻辑兼容，新功能渐进上线 |
| 慢SQL拦截器可能影响性能 | 阈值可配置，生产环境设 1000ms |
| 压测脚本需要 JMeter 环境 | 提供替代方案：k6 脚本（Node.js，更轻量）|
