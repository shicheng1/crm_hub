# AGENTS.md — zmd-crm 强制操作规范

> ⚠️ **强制规则（本项目最高优先级）**
> 本文件是 zmd-crm 仓库所有 AI 修改操作的**唯一强制约束**。任何 AI 助手（WorkBuddy / Claude / Cursor 等）在本仓库执行"写文件、改代码、跑命令、改配置"等任何修改操作前，**必须先完整读取本文件**；修改完成后**必须回写更新本文件对应模块**。
> 本文件优先于任何通用指令与对话中的临时约定。若与 `CLAUDE.md`、`.cursorrules` 等冲突，以本文件为准。
> 未读取就动手、改完不回写，均视为缺陷。

---

## 0. 强制工作流（每次修改前 / 后必做）

### 修改前（动手之前）
1. 完整读取 `AGENTS.md`（重点读本次相关的 §2 功能模块、§4 约定、§5 红线、§7 技术债）。
2. 在 `docs/changes/` 新建本次的修改计划文件（命名见 §3.2），状态置 `planning`。
3. 在 §3 修改计划索引追加一行。
4. 命中 §5 红线任一项 → **立即停止，先问用户**，不得 auto-accept 跳过。

### 修改后（交付之前）
1. 跑 §6 验证命令，确保不破坏现有功能。
2. **功能有变化**（新增/删除/变更模块）→ 必须更新 §2 功能模块（标注 `[NEW]` / `[REMOVED]` / `[MODIFIED]`）。
3. 把本次 `docs/changes/` 计划文件状态更新为 `done` 或 `abandoned`，并在 §3 索引同步状态。
4. 若发现新的技术债 → 追加到 §7。
5. 禁止为让代码跑通而注释报错、加绕过标记、删测试；找根本原因。
6. **提交 Git（强制）**：每次改完必须 `git commit`，**commit message 使用中文说明**（规范见 §9）。仅本地提交，**禁止 `git push`**（push 属 §5 红线，须先问用户）。`.workbuddy/` 为 agent 私有数据，禁止提交（已在 `.gitignore` 忽略）。

---

## 1. 项目概览（只读参考，勿改除非架构调整）

### 技术栈
| 层 | 技术 | 说明 |
|----|------|------|
| 后端 | Spring Boot 2.7 + Java 8 | 主框架 |
| ORM | MyBatis-Plus 3.5.3.1 | 注解式 Mapper，**无 XML** |
| 数据库 | MySQL 8 | 业务持久化 |
| 缓存/锁/限流 | Redis | `order:token:*` / `order:cache:*` / `order:lock:*` / `order:rate:*` |
| 消息队列 | RabbitMQ | 审批通知异步，可 `mq.enabled=false` 降级同步 |
| 实时推送 | WebSocket (STOMP) | `/topic/notifications/{userId}` |
| 认证 | JWT + BCrypt | 无状态 + Redis 主动注销 |
| 脚本 | Lua | 分布式锁释放、滑动窗口限流 |
| 迁移 | Flyway | `backend/src/main/resources/db/migration/V1~V7` |
| 前端 | Vue 3 + Vite 5 + Element Plus | Router4 / Axios / STOMP.js+SockJS |
| 部署 | Docker Compose | Nginx + jar + MySQL + Redis + RabbitMQ |

### 端口与地址
| 服务 | 端口 |
|------|------|
| 前端 | 80（dev: 5173） |
| 后端 | 8080（健康检查 `/actuator/health`） |
| MySQL | 3306 |
| Redis | 6379 |
| RabbitMQ | 5672 / 管理台 15672 |

### 默认账号（测试）
`admin / 123456`（审批人）、`user1 / 123456`、`user2 / 123456`（普通用户）

### 启动命令
```bash
# 后端
cd backend && mvn spring-boot:run            # http://localhost:8080
# 前端
cd frontend && npm install && npm run dev    # http://localhost:5173
# 测试
cd backend && mvn test
cd frontend && npm run build
```

---

## 2. 功能模块（每次新增 / 变更功能必更新）

> 规则：新增功能 → 追加一行并标 `[NEW]`；删除 → 标 `[REMOVED]`；行为/接口变更 → 标 `[MODIFIED]`。
> 本表是功能真实清单，不是 README 的营销稿；以代码事实为准。

| 编号 | 功能 | 关键路径（点击跳转源码） | 状态 |
|------|------|----------|------|
| F01 | 用户认证与授权（登录/登出/JWT/Redis token/refresh） | [auth/](backend/src/main/java/com/zmd/order/auth/) | `[DONE]` |
| F02 | 工单管理（创建/列表/详情/待办/已办/退回重提） | [service/impl/OrderServiceImpl.java](backend/src/main/java/com/zmd/order/service/impl/OrderServiceImpl.java) | `[DONE]` |
| F03 | 多级审批流引擎（N级步骤/多审批人/相同审批人跳过/三种驳回策略 RESTART·PREVIOUS·ORIGIN） | [approval/](backend/src/main/java/com/zmd/order/approval/) | `[DONE]` |
| F04 | 审批模式评估器（决定审批推进/驳回路径） | [approval/ApprovalModeEvaluator.java](backend/src/main/java/com/zmd/order/approval/ApprovalModeEvaluator.java) | `[DONE]` |
| F05 | 实时通知（WebSocket STOMP 推送 + RabbitMQ 异步，可降级） | [websocket/NotificationService.java](backend/src/main/java/com/zmd/order/websocket/NotificationService.java) · [mq/ApprovalProducer.java](backend/src/main/java/com/zmd/order/mq/ApprovalProducer.java) | `[DONE]` |
| F06 | 数据看板（状态分布/今日新增今日通过/近7天趋势/5分钟缓存） | [service/impl/DashboardServiceImpl.java](backend/src/main/java/com/zmd/order/service/impl/DashboardServiceImpl.java) | `[DONE]` |
| F07 | 分布式锁（Redis `SET NX EX` + Lua 释放，防重复提交/并发审批） | [lock/RedisDistributedLock.java](backend/src/main/java/com/zmd/order/lock/RedisDistributedLock.java) | `[DONE]` |
| F08 | 接口限流（Redis+Lua 滑动窗口，按用户/IP） | [rate/](backend/src/main/java/com/zmd/order/rate/) · [lua/rate_limit.lua](backend/src/main/resources/lua/rate_limit.lua) | `[DONE]` |
| F09 | 缓存（工单详情 Cache Aside + 空值缓存 + 看板统计缓存） | [cache/OrderCacheService.java](backend/src/main/java/com/zmd/order/cache/OrderCacheService.java) | `[DONE]` |
| F10 | 操作/审计日志（全程可追溯） | [entity/OrderOperationLog.java](backend/src/main/java/com/zmd/order/entity/OrderOperationLog.java) | `[DONE]` |
| F11 | 慢 SQL 拦截器（MyBatis Interceptor 统计执行时长） | [config/SlowSqlInterceptor.java](backend/src/main/java/com/zmd/order/config/SlowSqlInterceptor.java) | `[DONE]` |
| F12 | 定时任务（超时工单自动关闭） | [schedule/](backend/src/main/java/com/zmd/order/schedule/) | `[DONE]` |

---

## 3. 修改计划模块（Change Plans）

### 3.1 规则
- 每次修改在 `docs/changes/` 下建**独立文件**，一个修改一个文件，不共用。
- 文件命名：`CC-YYYY-MM-DD-NN-slug.md`（NN 为当日两位序号，从 01 起）。
- 文件状态字段：`planning` / `in-progress` / `done` / `abandoned`。
- 本索引表与 `docs/changes/` 文件必须一一对应，状态保持同步。
- **全文档文件引用必须带 Markdown 相对路径超链接（全局规则）**：凡在 AGENTS.md 中出现文件路径、计划文件、章节引用，一律写成可点击链接，禁止纯文本路径。格式 `[path/to/file.ext](path/to/file.ext)`；目录链接指向目录（`[docs/changes/](docs/changes/)`）；指向本文件章节用锚点（如 `[§3](#3-修改计划模块change-plans)`，渲染器支持时才用）。适用范围：§2 关键路径、§3.3 索引文件列、§7 技术债 CC 引用、§8 文档索引，以及今后任何新增的文件/计划引用。**纯文本路径视为文档缺陷**，每次回写 AGENTS.md 时一并修正。
- 大改动（涉及多文件/架构/DB）先在 Plan 模式出方案，用户确认后再动手。

### 3.2 计划文件模板
```markdown
# CC-YYYY-MM-DD-NN <标题>

- 状态：planning
- 关联技术债：§7 编号（如有）
- 创建：YYYY-MM-DD
- 修改人：AI / 用户

## 背景
为什么改（问题本质，非空话）

## 影响范围
- 文件：...
- 接口：...
- 是否触碰红线：否 / 是（已获用户确认）

## 方案
1. ...
2. ...

## 验证
- 命令：...
- 预期：...

## 回滚
- 如何回退
```

### 3.3 修改计划索引
| 编号 | 日期 | 标题 | 文件 | 状态 | 关联 |
|------|------|------|------|------|------|
| CC-2026-07-07-01 | 2026-07-07 | P0 性能修复（看板合并查询 / 列表 enrich 批量化 / 锁内通知解耦） | [docs/changes/CC-2026-07-07-01-p0-perf-fixes.md](docs/changes/CC-2026-07-07-01-p0-perf-fixes.md) | `done` | §7 P0-1/2/3 |
| CC-2026-07-07-02 | 2026-07-07 | 架构解耦重构（拆审批引擎 + 领域事件解耦缓存/通知，搭骨架） | [docs/changes/CC-2026-07-07-02-arch-refactor.md](docs/changes/CC-2026-07-07-02-arch-refactor.md) | `done` | §7 A1/A2/A3 |
| CC-2026-07-07-03 | 2026-07-07 | 前端优化改造（request 挂起修复/用户字典缓存/死代码/row-key/WebSocket 健壮/GET 去重） | [docs/changes/CC-2026-07-07-03-frontend-opt.md](docs/changes/CC-2026-07-07-03-frontend-opt.md) | `done` | §7 前端技术债 |
| CC-2026-07-07-04 | 2026-07-07 | 后端收尾（api.md 补 /auth/refresh + 债项复核） | [docs/changes/CC-2026-07-07-04-backend-doc.md](docs/changes/CC-2026-07-07-04-backend-doc.md) | `done` | §7 P2-3 |
| CC-2026-07-07-05 | 2026-07-07 | 前端业务流转后视图刷新与跳转（orderBus 总线 + 列表/看板自动重拉 + 详情操作后跳转对应列表） | [docs/changes/CC-2026-07-07-05-frontend-flow-refresh.md](docs/changes/CC-2026-07-07-05-frontend-flow-refresh.md) | `done` | §7 F-P3 |
| CC-2026-07-07-06 | 2026-07-07 | 锁模板化（A2 阶段二：抽 LockTemplate 隔离锁横切 + 修正释放早于提交） | [docs/changes/CC-2026-07-07-06-lock-templating.md](docs/changes/CC-2026-07-07-06-lock-templating.md) | `done` | §7 A2 / P0-3 |

---

## 4. 架构与代码约定（禁止随意改动）

- **目录结构**：后端包按 `auth/cache/common/config/controller/dto/entity/lock/mapper/mq/rate/schedule/service/websocket` 划分，新增能力先归包，不堆在根。
- **Mapper**：MyBatis-Plus 注解式，**不要引入 XML**，复杂 SQL 用 `@Select`/`@Update` + `<script>`。
- **数据库变更**：**必须**走 Flyway 新增 `V{N+1}__*.sql`，禁止手改已有表或加 `@Column` 后手动 ALTER；V1~V7 已基线，新迁移从 V8 起。
- **缓存约定**：工单详情用 Cache Aside（先更库再删缓存）；看板统计缓存 5 分钟，审批操作后主动失效。
- **分布式锁**：`order:lock:{orderId}`，lease 默认 60s；释放须用 Lua 原子删；**锁内不得做慢操作**（见 §7 P0-3）。
- **限流**：`rate_limit.lua` 单脚本原子；新增限流用 `@RateLimit` 注解，不要散写逻辑。
- **命名**：Java 驼峰、表/字段小写下划线；前端组件 PascalCase、API 函数小驼峰。
- **不要为了跑通注释报错或加绕过标记**；找根因。

---

## 5. 红线（必须先问用户，auto-accept 也不能跳过）

以下操作**即使开启自动接受也必须暂停询问**：
- 删除文件、目录或 git 历史
- 修改 `.env`、密钥、token、CI/CD 配置
- 数据库 schema 变更或数据迁移（含 Flyway 脚本是否执行）
- `git push` / `git rebase` / `git reset --hard` / 强制推送
  > 注：`git commit`（本地提交）**不在红线**，是 §0 强制动作；上述红线仅限 push / rebase / reset --hard / 强制推送等会改变远端或历史记录的操作。
- 安装新的全局依赖或修改系统配置
- 公开发布（npm publish、部署到生产、发文章等）

---

## 6. 验证规范（改完必须跑）

后端：
```bash
cd backend && mvn -q test        # 单测不过不准交付
# 接口手验（示例）
curl -s localhost:8080/api/dashboard/stats -H "Authorization: Bearer $TOKEN"
```
前端：
```bash
cd frontend && npm run build     # 构建通过
```
专项（性能类修改必做）：
```bash
BASE_URL=http://localhost:8080 USERNAME=admin PASSWORD=123456 k6 run docs/performance/load-test.js
```
任何修改不得降低现有接口正确性与单测通过率；性能修改须用 k6 复测并对比 §7 基线。

---

## 7. 已知技术债 / TODO（审查发现，关联修改计划）

按影响排序；修复后在此标记 `[FIXED]` 并关联 `docs/changes/` 编号。

### P0（性能 / 正确性，优先修）
| 编号 | 问题 | 证据 | 影响 | 状态 |
|------|------|------|------|------|
| P0-1 | 看板统计 22 次独立聚合查询 | `DashboardServiceImpl` `getStats` 8×COUNT + `getTrend` 14×COUNT | 进看板打满 22 条扫描，缓存一失效重打 | `[FIXED]`（[CC-2026-07-07-01](docs/changes/CC-2026-07-07-01-p0-perf-fixes.md)，Java8 验证通过） |
| P0-2 | 列表 `enrichOrder` 逐条 N+1 | `OrderServiceImpl` 每条工单调快照+记录+创建人 | 一页10条≈30次查询，README 称已消除却回归 | `[FIXED]`（[CC-2026-07-07-01](docs/changes/CC-2026-07-07-01-p0-perf-fixes.md)，Java8 验证通过） |
| P0-3 | 分布式锁 lease 短 + 锁内慢操作 | `OrderServiceImpl` 锁内同步 WebSocket/MQ/多表写；`finally` 释放早于 commit | 超60s或通知慢→锁过期→并发双审；释放早于提交 | 部分 `[FIXED]`：通知解耦已由 [CC-2026-07-07-02](docs/changes/CC-2026-07-07-02-arch-refactor.md) 完成；锁释放时机/lease 归 A2（阶段二锁模板化，[FIXED]（[CC-2026-07-07-06](docs/changes/CC-2026-07-07-06-lock-templating.md)，Java8 待验证）） |

### P1（收益明显，次之）
| 编号 | 问题 | 证据 | 状态 |
|------|------|------|------|
| P1-1 | 缓存命中后仍 re-enrich | `OrderServiceImpl` `return enrichOrder(cached)` | 待修 |
| P1-2 | 同单快照反复解析 | `getRejectMode`/`enrichOrder`/`getStep` 各自 `getFlowSnapshot` | 待修 |
| P1-3 | 分页 `size` 无上限 | `pageOrders` 直接 `new Page<>(page,size)` | 待修 |

### P2（清理 / 文档）
| 编号 | 问题 | 证据 | 状态 |
|------|------|------|------|
| P2-1 | 死字段 `approval_flow_step.approver_id` | `V2__approval_flow.sql`；`isStepApprover` 只用 `approval_step_approver` | 待清理 |
| P2-2 | 锁忙等 `Thread.sleep(50)` | `RedisDistributedLock` | 可接受，记录 |
| P2-3 | `docs/api.md` 缺 `/auth/refresh` | 登录返回 refreshToken 但文档未记 | `[FIXED]`（[CC-2026-07-07-04](docs/changes/CC-2026-07-07-04-backend-doc.md)） |

---

### 架构债（高内聚低耦合维度，待重构）
| 编号 | 问题 | 证据 | 状态 |
|------|------|------|------|
| A1 | 上帝类：`OrderServiceImpl` 585行/注入15 bean/直接持有8 Mapper，工单CRUD+审批引擎+缓存失效+锁+通知+MQ 混一体 | `OrderServiceImpl:51-64` 依赖注入；585行 vs 其他service 40-150行 | 已实现（[CC-2026-07-07-02](docs/changes/CC-2026-07-07-02-arch-refactor.md) 阶段一拆出 `ApprovalEngineService`，Java8 验证通过） |
| A2 | 横切关注点未隔离：锁/缓存/通知/MQ 具体实现硬编码方法体内，无 AOP/事件抽象 | `approveOrder` 内同步调 `redisLock`/`orderCacheService`/`notificationService`/`approvalProducer` | [CC-2026-07-07-02](docs/changes/CC-2026-07-07-02-arch-refactor.md)（阶段二锁模板化，[FIXED]（[CC-2026-07-07-06](docs/changes/CC-2026-07-07-06-lock-templating.md)，Java8 待验证）） |
| A3 | 反向依赖：`OrderServiceImpl` 注入 `DashboardService` 主动失效看板缓存，工单领域感知看板存在 | `OrderServiceImpl:64` 注入 `DashboardService`；`DashboardServiceImpl` 不反向依赖 `OrderService`（单向越界） | 已实现（[CC-2026-07-07-02](docs/changes/CC-2026-07-07-02-arch-refactor.md) 阶段一引入 `OrderApprovalEvent` + 监听器解耦，Java8 验证通过） |

### 前端技术债（CC-2026-07-07-03 已修复）
| 编号 | 问题 | 证据 | 状态 |
|------|------|------|------|
| F-P0-1 | 刷新失败致并发 401 请求永久挂起（页面卡死） | `request.js` `tryRefreshToken` 失败分支未 flush `pendingQueue` | `[FIXED]`（[CC-2026-07-07-03](docs/changes/CC-2026-07-07-03-frontend-opt.md)） |
| F-P1-1 | 每次进入详情/审批流页全量拉用户表 | `OrderDetail.vue:158` `FlowManage.vue:151` `getUserList` | `[FIXED]` 新增 `userDict.js` 共享字典 + TTL 缓存 |
| F-P1-2 | Element Plus 全量引入破坏 tree-shaking（首屏 element chunk 1MB） | `main.js:2-4` 全量 import + 全量图标注册 | 待确认：需装 `unplugin-vue-components`+`unplugin-auto-import`（devDeps，红线），待用户批准 |
| F-P2-1 | 死代码 `refreshToken`/`getFlowDetail` | `api/auth.js:11` `api/flow.js:7` 定义但无人 import | `[FIXED]` 已删除 |
| F-P2-2 | 看板趋势 `barHeight` 每柱重复 `Math.max` 展开全数组 | `Dashboard.vue:92` | `[FIXED]` 提 `computed` |
| F-P2-3 | 四个 `el-table` 缺 `row-key` | `OrderList/TodoList/DoneList/UserManage` | `[FIXED]` 已补 `row-key="id"` |
| F-P2-4 | WebSocket 通知数组无上限 + 重连无退避/无 UI 提示 | `Layout.vue:117` `websocket.js:23-54` | `[FIXED]` 上限 50 + 指数退避 + 断线提示 |
| F-P2-5 | 路由切换重复拉取相同 GET（无缓存/去重层） | 全局（无 in-memory 缓存） | `[FIXED]` `request.js` 加 GET 并发去重 |
| F-P3 | 业务流转后视图不刷新 / 不跳转：WS 审批通知仅更新铃铛不联动列表/看板；详情页审批/重提后只原地刷新不跳转到对应列表 | `Layout.vue` onMessage / `OrderDetail.vue` handleApprove·handleResubmit / `TodoList`·`DoneList`·`OrderList`·`Dashboard` | `[FIXED]` 新增 [`orderBus.js`](frontend/src/utils/orderBus.js) 总线 + 列表/看板 `watch` 自动重拉；详情操作后 `router.push` 到 `/todo`·`/orders`；`DoneList` 补刷新按钮与 `loading` |

## 8. 文档索引（现有 docs，勿重复造轮子）
| 文档 | 路径（点击跳转） |
|------|------|
| API 接口 | [docs/api.md](docs/api.md) |
| 架构 | [docs/architecture.md](docs/architecture.md) |
| 数据库设计 | [docs/database.md](docs/database.md) |
| 部署 | [docs/deployment.md](docs/deployment.md) |
| 安全 | [docs/security.md](docs/security.md) |
| 性能优化 | [docs/performance.md](docs/performance.md) |
| 压测脚本 | [docs/performance/](docs/performance/) |
| 测试说明 | [docs/testing.md](docs/testing.md) |
| 故障排查 | [docs/troubleshooting.md](docs/troubleshooting.md) |
| 生产升级路线图 | [docs/plans/production-upgrade-roadmap.md](docs/plans/production-upgrade-roadmap.md) |
| 修改计划 | [docs/changes/](docs/changes/)（本文件 §3 管理） |

---

## 9. Git 提交规范（强制）

- **每次改完必须提交**：完成一轮修改（代码 / 文档 / 配置）后立即 `git commit`，不得长期堆积未提交改动。
- **commit message 使用中文说明**：
  - 首行：中文摘要，精炼描述本次改动。建议格式 `类型: 中文简述`，类型取 `feat` / `fix` / `refactor` / `perf` / `docs` / `chore` / `test` 之一，冒号后用中文。
  - 正文：按需列出关键改动点，并关联 `docs/changes/` 编号（如 `CC-2026-07-07-01`）。
  - 禁止无意义 message（如 `update`、`fix`、`temp`、`asdf`）。
- **仅本地提交，不推送**：`git push` 属 §5 红线，未经用户确认不得执行。
- **提交范围**：只提交本次改动相关文件；agent 私有数据 `.workbuddy/` 禁止提交（已在 `.gitignore` 忽略）。
- **禁止**：为绕过校验而 `git commit --no-verify`、空提交、`git commit --amend` 覆盖已推送历史。
