# 工单审批系统（Order Approval System）

一个面向企业内部的工单审批系统，支持多级审批流、多审批人、驳回策略、实时通知等特性。项目采用 Spring Boot + Vue3 全栈技术栈，覆盖后端服务、前端界面、消息队列、缓存、分布式锁等常见企业级技术点。

> 📋 **项目治理**：所有 AI 辅助修改操作受 [AGENTS.md](AGENTS.md) 强制约束。功能模块权威清单见该文件 §2，已知技术债与优化计划见 §7，修改历史见 [docs/changes/](docs/changes/)。改动前请先阅读。

## 目录

- [项目状态总览](#项目状态总览)
- [技术栈](#技术栈)
- [核心功能](#核心功能)
- [项目结构](#项目结构)
- [功能模块](#功能模块)
- [快速开始](#快速开始)
- [性能压测](#性能压测)
- [文档](#文档)
- [架构要点](#架构要点)
- [配置说明](#配置说明)
- [已知问题与进行中优化](#已知问题与进行中优化)
- [License](#license)

## 项目状态总览

> 本仓库的总控视图。细节以 [AGENTS.md](AGENTS.md) 为准；本节省略证据，便于一眼掌控全局。

### 当前阶段
- **架构重构（阶段一）已编码、待验证**：审批引擎拆出 `ApprovalEngineService`，缓存失效与通知改为领域事件驱动（[CC-2026-07-07-02](docs/changes/CC-2026-07-07-02-arch-refactor.md)，`in-progress`）。需在 **Java 8** 环境 `mvn test` + 手验审批链路（通过/驳回三种模式/WebSocket 通知/看板缓存失效）。
- **P0 性能优化实施中**：看板合并查询（22→3 次 SQL）/ 列表 enrich 批量化（消灭 N+1）/ 锁内通知解耦（已由 CC-02 完成）代码已落地，待 **Java 8** 环境 `mvn test` + 手验（[CC-2026-07-07-01](docs/changes/CC-2026-07-07-01-p0-perf-fixes.md)，`in-progress`）。

### 进行中变更
| 计划 | 标题 | 状态 | 关联债 |
|------|------|------|--------|
| [CC-2026-07-07-02](docs/changes/CC-2026-07-07-02-arch-refactor.md) | 架构解耦重构（搭骨架） | `in-progress` | A1/A3 已实现待验证；A2 待排期 |
| [CC-2026-07-07-01](docs/changes/CC-2026-07-07-01-p0-perf-fixes.md) | P0 性能修复（后端） | `in-progress` | P0-1 / P0-2 / P0-3（待 Java8 验证） |
| [CC-2026-07-07-03](docs/changes/CC-2026-07-07-03-frontend-opt.md) | 前端优化改造 | `done` | F-P0-1 / F-P1-1 / F-P2-1~5 |
| [CC-2026-07-07-04](docs/changes/CC-2026-07-07-04-backend-doc.md) | 后端文档收尾 | `done` | P2-3 |

### 技术债速览
**后端**
- **P0 ×3**：看板 22 次 `COUNT`（P0-1，已改 3 次 SQL 待验证）、列表 N+1（P0-2，已批量 enrich 待验证）、锁内慢操作（P0-3，通知已由 CC-02 移出锁）
- **P1 ×3**：缓存命中仍 re-enrich（P1-1，已修）、同单快照反复解析（P1-2，已缓解）、分页 `size` 无上限（P1-3，已修）
- **P2 ×3**：死字段 `approver_id`（P2-1，保留）、锁忙等 `sleep`（P2-2，可接受）、`api.md` 缺 `/auth/refresh`（P2-3，已补）
- **架构债**：A1✓ A3✓（已实现待验证）/ A2 待排期（锁模板化）

**前端**（[CC-2026-07-07-03](docs/changes/CC-2026-07-07-03-frontend-opt.md) 已修复，`npm run build` 通过）
- F-P0-1 刷新失败致并发请求挂起（已修）· F-P1-1 每次进详情全量拉用户表（已改共享字典缓存）· F-P2-1 死代码（已删）· F-P2-2 看板 barHeight 重复 max（已提 computed）· F-P2-3 四表格缺 row-key（已补）· F-P2-4 WebSocket 通知无上限/重连脆弱（已修）· F-P2-5 路由切换重复 GET（已加并发去重）
- **待确认**：F-P1-2 Element Plus 全量引入（首屏 element chunk 1MB），需装 devDeps 做按需引入（红线，待批准）

- 完整明细与证据见 [AGENTS.md §7](AGENTS.md)

### 环境与常用入口
| 入口 | 地址 |
|------|------|
| 前端（dev） | http://localhost:5173 |
| 后端 | http://localhost:8080 |
| 健康检查 | http://localhost:8080/actuator/health |
| RabbitMQ 管理台 | http://localhost:15672 |
| 修改计划 | [docs/changes/](docs/changes/) |
| 强制规范 | [AGENTS.md](AGENTS.md) |

### 常用命令
```bash
cd backend && mvn spring-boot:run     # 启动后端（:8080）
cd backend && mvn test                # 后端单测
cd frontend && npm run dev            # 启动前端（:5173）
cd frontend && npm run build          # 前端构建
BASE_URL=http://localhost:8080 USERNAME=admin PASSWORD=123456 k6 run docs/performance/load-test.js  # 压测
```

## 技术栈

### 后端
- **Spring Boot 2.7** + **Java 8**
- **MyBatis-Plus 3.5**：ORM + 分页插件
- **MySQL 8**：业务数据存储
- **Redis**：缓存（工单详情、看板统计）、分布式锁、限流计数、JWT token 管理
- **RabbitMQ**：审批通知异步消息（手动 ACK，可关闭）
- **WebSocket (STOMP)**：实时通知推送
- **JWT**：无状态认证
- **BCrypt**：密码加密
- **Lua 脚本**：分布式锁释放、滑动窗口限流

### 前端
- **Vue 3** + **Vite 5**
- **Element Plus**：UI 组件库
- **Vue Router 4**：路由
- **Axios**：HTTP 请求
- **STOMP.js + SockJS**：WebSocket 客户端

## 核心功能

### 1. 多级审批流
- 可配置的审批步骤（N 级），每步可配置多个审批人（任一通过即推进）
- 相同审批人连续步骤自动跳过
- 三种驳回策略：
  - `RESTART`：回到第一步重新审批
  - `PREVIOUS`：退回上一步
  - `ORIGIN`：退回发起人重新提交

### 2. 工单管理
- 创建工单（选择审批流）
- 工单列表（支持状态筛选、标题搜索、分页）
- 工单详情（审批进度可视化、操作日志时间线）
- 待办列表（当前用户需要审批的工单）
- 已办列表（当前用户已审批的工单）
- 退回后重新提交

### 3. 实时通知
- 审批结果通过 WebSocket 实时推送给工单创建人
- 前端通知中心（未读计数、通知列表）
- RabbitMQ 异步处理通知消息（可降级为同步）

### 4. 数据看板
- 工单状态分布统计
- 今日新增 / 今日通过
- 近 7 天趋势图
- Redis 缓存统计结果（5 分钟过期）

### 5. 系统特性
- JWT 无状态认证 + Redis 主动注销
- 分布式锁防止并发审批冲突
- 接口限流（Redis + Lua 滑动窗口，按用户/IP）
- 全局异常处理统一返回格式
- 缓存穿透防护（空值缓存）
- 定时任务自动关闭超时工单

## 项目结构

```
zmd-crm/
├── AGENTS.md                         # 项目强制操作规范（AI 修改治理）
├── backend/                          # 后端服务
│   ├── src/main/java/com/zmd/order/
│   │   ├── OrderApplication.java     # 启动类（@EnableAsync 已开启）
│   │   ├── auth/                     # 认证（JWT、拦截器、登录）
│   │   ├── cache/                    # Redis 缓存服务
│   │   ├── common/                   # 常量、统一返回、异常处理
│   │   ├── config/                   # 配置（Redis、RabbitMQ、MyBatis、WebMvc）
│   │   ├── controller/               # 控制器
│   │   ├── dto/                      # 数据传输对象（含 ApprovalOutcome 审批产出）
│   │   ├── entity/                   # 实体类
│   │   ├── approval/                 # 审批模式评估器（ApprovalModeEvaluator）
│   │   ├── approval/engine/          # 审批引擎（重构后从 OrderServiceImpl 拆出）
│   │   ├── event/                    # 领域事件（OrderApprovalEvent + 缓存失效/通知监听器）
│   │   ├── lock/                     # 分布式锁
│   │   ├── mapper/                   # MyBatis Mapper（注解式，无 XML）
│   │   ├── mq/                       # RabbitMQ 生产者/消费者
│   │   ├── rate/                     # 限流
│   │   ├── schedule/                 # 定时任务
│   │   ├── service/                  # 业务服务（OrderService 为编排层）
│   │   ├── statemachine/             # 状态机（OrderStatusTransition）
│   │   └── websocket/                # WebSocket 通知
│   └── src/main/resources/
│       ├── db/migration/             # Flyway 迁移脚本（V1~V7，新迁移从 V8 起）
│       ├── lua/                      # Lua 脚本（锁释放、限流）
│       └── application*.yml          # 配置文件
├── frontend/                         # 前端
│   └── src/
│       ├── api/                      # API 请求
│       ├── components/               # 组件（Layout）
│       ├── router/                   # 路由
│       ├── utils/                    # 工具（请求、认证、WebSocket）
│       └── views/                    # 页面
├── docs/
│   ├── changes/                      # 修改计划（由 AGENTS.md §3 管理）
│   └── ...                           # 其余文档见下方「文档」一节
└── README.md
```

## 功能模块

> 权威、可点击的功能模块清单见 [AGENTS.md §2](AGENTS.md)；下表为阅读视图，「关键路径」链接到对应源码，模块状态变动以 AGENTS.md 为准。

| 编号 | 模块 | 说明 | 关键路径 |
|------|------|------|----------|
| F01 | 用户认证与授权 | 登录 / 登出 / JWT / Redis token 管理 / refresh | [auth/](backend/src/main/java/com/zmd/order/auth/) |
| F02 | 工单管理 | 创建 / 列表 / 详情 / 待办 / 已办 / 退回重提 | [OrderServiceImpl](backend/src/main/java/com/zmd/order/service/impl/OrderServiceImpl.java) |
| F03 | 多级审批流引擎 | N 级步骤 / 多审批人 / 相同审批人跳过 / 三种驳回策略 | [approval/](backend/src/main/java/com/zmd/order/approval/) |
| F04 | 审批模式评估器 | 决定审批推进 / 驳回路径 | [ApprovalModeEvaluator](backend/src/main/java/com/zmd/order/approval/ApprovalModeEvaluator.java) |
| F05 | 实时通知 | WebSocket STOMP 推送 + RabbitMQ 异步（可降级） | [websocket/](backend/src/main/java/com/zmd/order/websocket/) · [mq/](backend/src/main/java/com/zmd/order/mq/) |
| F06 | 数据看板 | 状态分布 / 今日新增通过 / 近 7 天趋势 / 5 分钟缓存 | [DashboardServiceImpl](backend/src/main/java/com/zmd/order/service/impl/DashboardServiceImpl.java) |
| F07 | 分布式锁 | Redis SET NX EX + Lua 释放，防重复提交 / 并发审批 | [RedisDistributedLock](backend/src/main/java/com/zmd/order/lock/RedisDistributedLock.java) |
| F08 | 接口限流 | Redis + Lua 滑动窗口，按用户 / IP | [rate/](backend/src/main/java/com/zmd/order/rate/) · [rate_limit.lua](backend/src/main/resources/lua/rate_limit.lua) |
| F09 | 缓存 | 工单详情 Cache Aside + 空值缓存 + 看板统计缓存 | [OrderCacheService](backend/src/main/java/com/zmd/order/cache/OrderCacheService.java) |
| F10 | 操作 / 审计日志 | 全程可追溯 | [OrderOperationLog](backend/src/main/java/com/zmd/order/entity/OrderOperationLog.java) |
| F11 | 慢 SQL 拦截器 | MyBatis Interceptor 统计执行时长 | [SlowSqlInterceptor](backend/src/main/java/com/zmd/order/config/SlowSqlInterceptor.java) |
| F12 | 定时任务 | 超时工单自动关闭 | [schedule/](backend/src/main/java/com/zmd/order/schedule/) |

各模块的业务细节见下方「核心功能」。

## 快速开始

### 环境要求
- JDK 8+
- MySQL 8+
- Redis 5+
- RabbitMQ 3.8+（可选，可通过 `mq.enabled=false` 关闭）
- Node.js 16+

### 1. 初始化数据库

数据库结构现已由 **Flyway** 托管（`backend/src/main/resources/db/migration/V1~V7`），应用启动时自动建表/升级，**无需手动执行 SQL**。

```bash
# 先创建空数据库（Flyway 不会自动建库）
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS order_approval DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_general_ci;"

# 全新空库：直接启动应用即可，Flyway 会自动执行 V1~V7。
# 已有旧库（已含全部表）：先打基线，避免 Flyway 重复建表报错：
#   mvn flyway:baseline -Dflyway.baselineVersion=7
```

> Flyway 配置见 `application.yml`（`spring.flyway`：`baseline-on-migrate=true`、`baseline-version=7`）。
> 若要彻底重建库，先 `DROP DATABASE order_approval` 再重建空库，并把 `baseline-version` 临时改为 `0` 后启动应用。

### 2. 启动后端
```bash
cd backend
# 修改 application-dev.yml 中的数据库/Redis/RabbitMQ 连接信息
mvn spring-boot:run
# 后端启动在 http://localhost:8080
```

### 3. 启动前端
```bash
cd frontend
npm install
npm run dev
# 前端启动在 http://localhost:5173
```

### 4. 生产化 Docker Compose 演示
```bash
cp .env.example .env
# 修改 .env 中的密码和 JWT_SECRET
docker compose up -d --build
```

访问地址：
- 前端：http://localhost
- 后端健康检查：http://localhost:8080/actuator/health
- RabbitMQ 管理台：http://localhost:15672

部署细节见：`docs/deployment.md`

### 5. 测试与构建
```bash
cd backend && mvn test
cd frontend && npm run build
```

### 6. 测试账号
| 用户名 | 密码 | 角色 |
|--------|------|------|
| admin | 123456 | 审批人 |
| user1 | 123456 | 普通用户 |
| user2 | 123456 | 普通用户 |

## 性能压测

压测脚本与基准报告放在 `docs/performance/`：

| 文件 | 说明 |
|------|------|
| `docs/performance/load-test.js` | **推荐**：k6 压测脚本（Node 生态，单二进制，登录仅执行一次避免触发限流） |
| `docs/performance/jmeter-test-plan.jmx` | JMeter 5.x 测试计划（Setup 线程组登录一次，主线程组对看板/工单接口加压） |
| `docs/performance/baseline-report.md` | 基准报告模板与运行说明 |

### 用 k6 跑（最快）

```bash
# 安装：https://k6.io/docs/get-started/installation/
BASE_URL=http://localhost:8080 USERNAME=admin PASSWORD=123456 k6 run docs/performance/load-test.js
# 自定义规模
k6 run --vus 100 --duration 3m docs/performance/load-test.js
```

### 用 JMeter 跑

```bash
jmeter -n -t docs/performance/jmeter-test-plan.jmx -l result.jtl
jmeter -g result.jtl -o report-html   # 生成 HTML 报告
```

> ⚠️ 注意：`/api/auth/login` 有 `@RateLimit(auth:login, 5次/分钟)` 限流。两套脚本都**只在测试开始时登录一次**并复用 token。若要单独对登录接口加压，请临时调大或关闭该限流。

## 文档

| 文档 | 路径（点击跳转） |
|------|------|
| **项目强制规范（AI 修改治理）** | [AGENTS.md](AGENTS.md) |
| 修改计划（Change Plans） | [docs/changes/](docs/changes/) |
| API 接口文档 | [docs/api.md](docs/api.md) |
| 架构文档 | [docs/architecture.md](docs/architecture.md) |
| 数据库设计 | [docs/database.md](docs/database.md) |
| 部署说明 | [docs/deployment.md](docs/deployment.md) |
| 用户管理需求 | [docs/user-management.md](docs/user-management.md) |
| 面试讲解稿 | [docs/interview.md](docs/interview.md) |
| 演示脚本 | [docs/demo-script.md](docs/demo-script.md) |
| 安全文档 | [docs/security.md](docs/security.md) |
| 性能优化 | [docs/performance.md](docs/performance.md) |
| 压测脚本 | [docs/performance/](docs/performance/) |
| 测试说明 | [docs/testing.md](docs/testing.md) |
| 故障排查 | [docs/troubleshooting.md](docs/troubleshooting.md) |
| 生产级升级路线图 | [docs/plans/production-upgrade-roadmap.md](docs/plans/production-upgrade-roadmap.md) |

## 架构要点

### 1. 分布式锁实现（RedisDistributedLock）
- **加锁**：`SET key value NX EX` 原子命令，value 为 UUID+线程ID
- **释放**：Lua 脚本保证「检查 + 删除」原子性，防止误删他人锁
- **应用场景**：工单创建（防重复提交）、工单审批（防并发审批）

### 2. 缓存设计（Cache Aside Pattern）
- 工单详情缓存：先更新数据库，再删除缓存
- 防穿透：空值缓存（短 TTL）
- 看板统计缓存：5 分钟过期，审批操作后主动失效

### 3. 多级审批流引擎
- 步骤配置 + 多审批人（任一通过即推进）
- 相同审批人跳过：避免重复审批
- 三种驳回策略：RESTART / PREVIOUS / ORIGIN
- 审批记录全程可追溯

### 4. 消息队列异步通知
- RabbitMQ 手动 ACK，消费失败不重新入队（防无限重试）
- 生产者通过 `ObjectProvider` 注入，支持 MQ 关闭时降级
- WebSocket 实时推送给前端

### 5. 接口限流（滑动窗口）
- Redis + Lua 脚本实现滑动窗口限流
- 按用户 ID（已登录）或 IP（未登录）限流
- 登录接口限流防暴力破解

### 6. 列表查询与批量加载（N+1 部分优化）
- 工单分页列表：一次性 `IN` 批量加载创建人姓名，避免逐条查用户（无 N+1）
- 待办/已办列表：单条 SQL `JOIN` 查出「我的待办」，但展示信息（审批流快照、审批记录）仍按工单逐条 `enrich`，**尚未完全消除 N+1**（见 AGENTS.md §7 P0-2，已在优化计划中）
- MyBatis-Plus 分页插件配置

## 面试常见问题

> 面试向的问答、讲法与简历写法统一维护在 [docs/interview.md](docs/interview.md)（避免与工程总控视图重复）。

## 配置说明

### 环境变量（生产环境推荐）
| 变量名 | 说明 | 默认值 |
|--------|------|--------|
| JWT_SECRET | JWT 签名密钥 | zmd-order-approval-secret-key-2024-please-change-in-prod |
| JWT_EXPIRE_HOURS | JWT 过期时间（小时） | 24 |

### MQ 开关
`application-dev.yml` 中 `mq.enabled=true` 启用 RabbitMQ，设为 `false` 时通知降级为同步 WebSocket 推送。

## 已知问题与进行中优化

本项目已知技术债与优化项统一维护在 [AGENTS.md §7](AGENTS.md)（按 P0 / P1 / P2 与架构债分级）。

当前重点：

- **P0 性能**：看板统计已合并为 3 次聚合（P0-1）、待办/已办列表已批量 enrich（P0-2，待验证）；锁内通知已由架构重构移出（见 [docs/changes/CC-2026-07-07-02](docs/changes/CC-2026-07-07-02-arch-refactor.md)，P0-3 收口见 A2）
- **架构**：审批引擎已从上帝类 `OrderServiceImpl` 拆出为 `ApprovalEngineService`；缓存失效与通知改为领域事件驱动（详见上述 change plan）

> 说明：README 是面向使用者与面试的阅读文档；功能模块与问题的权威清单以 [AGENTS.md](AGENTS.md) 为准。

## License
MIT
