# zmd-crm 生产级与面试亮点升级路线图

> **For Hermes:** 后续实现具体功能时，按 `test-driven-development` + `writing-plans` 执行；涉及多文件改造先拆小任务，避免一次性大改。

**Goal:** 把当前工单审批系统升级成一个可运行、可演示、可讲架构、可写进简历的生产级 Java/Vue 全栈项目。

**Architecture:** 当前项目已经具备审批流、JWT、Redis、RabbitMQ、WebSocket、限流、分布式锁、缓存、看板等雏形。下一步重点不是堆功能，而是补齐生产工程化、测试体系、安全治理、可观测性、部署交付和面试表达材料。

**Tech Stack:** Spring Boot 2.7 / Java 8 / MyBatis-Plus / MySQL / Redis / RabbitMQ / WebSocket / Vue3 / Vite / Element Plus。

---

## 0. 当前真实状态快照

检查时间：2026-07-06

### 代码结构

- 后端：`backend/`，Maven + Spring Boot 2.7.18。
- 前端：`frontend/`，Vue3 + Vite5。
- Java 源文件：49 个。
- Vue 页面：8 个。
- 前端 JS 文件：9 个。
- 当前 Git 分支：`master`。
- 当前只有 1 个提交：`init: 工单管理系统初始提交`。
- 当前工作区已有大量未提交改动，后续改造前应先整理成阶段性提交或至少打 tag/备份。

### 已验证

- 后端：`mvn -q test` 通过，但当前没有 `backend/src/test`，实际没有测试覆盖。
- 前端：`npm run build` 通过。
- 前端构建警告：主包 `index-*.js` 约 1.18MB，超过 Vite 500KB 警告，需要拆包优化。
- 当前没有 Dockerfile。
- 当前没有 docker-compose。
- 当前没有 CI/CD 配置。

### 已有亮点

- 多级审批流。
- 多审批人任一通过。
- 驳回策略：`RESTART` / `PREVIOUS` / `ORIGIN`。
- JWT + Redis token 主动失效。
- Redis 分布式锁。
- Redis + Lua 滑动窗口限流。
- Redis 缓存与空值缓存防穿透。
- RabbitMQ 异步通知。
- WebSocket 实时通知。
- 操作日志与审批记录。
- 看板统计。
- MyBatis-Plus 分页。
- 部分 N+1 查询优化。

### 当前主要短板

1. 没有自动化测试，`mvn test` 通过不能证明质量。
2. 没有生产环境配置分层，`application-dev.yml` 里仍有 root/root、guest/guest 等开发配置。
3. 没有 Docker / Compose / Nginx / CI/CD，面试讲部署会偏虚。
4. 前端没有 ESLint/Prettier/类型检查/测试。
5. 前端构建包过大，缺少手动拆包和路由级优化说明。
6. 缺少 API 文档、架构图、数据库设计文档、部署文档、压测报告。
7. 安全体系还不完整：权限模型、接口权限、刷新 token、密码升级、审计日志、防重复提交策略都可继续加强。
8. 缺少可观测性：Actuator、健康检查、指标、日志 traceId、慢 SQL 分析。
9. 缺少完整演示脚本：面试时 5 分钟怎么展示亮点还没固化。

---

## 1. 总体目标：简历出彩版本

建议把项目定位为：

> 企业级工单审批与流程协同系统。支持动态审批流、多级多审批人、驳回策略、实时通知、缓存加速、并发控制、接口限流、操作审计、数据看板和容器化部署。

简历可写方向：

- 负责审批流引擎设计，实现多级审批、多审批人、自动跳过、驳回回退等复杂状态流转。
- 使用 Redis 实现分布式锁、缓存、限流、JWT 主动失效，解决并发审批、缓存穿透、登录暴力破解等问题。
- 引入 RabbitMQ + WebSocket，实现审批结果异步处理与实时推送。
- 使用 MyBatis-Plus + 批量查询优化列表接口，减少 N+1 查询。
- 补齐 Docker Compose、Nginx、CI、健康检查、日志追踪、压测报告，形成完整生产交付闭环。

---

## 2. 升级优先级

### P0：先把工程质量打牢

目标：让项目稳定、可测、可运行、可回归。

任务：

1. 整理 Git 工作区：把当前已有改动按主题提交。
2. 增加后端测试依赖：JUnit5、Mockito、Spring Boot Test、H2 或 Testcontainers 方案二选一。
3. 先写核心纯逻辑测试：JWT、限流 key、审批状态流转策略、密码匹配。
4. 增加基础集成测试：登录、创建工单、审批流转。
5. 增加前端 lint/build 质量门禁。
6. 增加 `.env.example`，移除生产敏感默认值表达。

验收：

```bash
cd backend && mvn test
cd frontend && npm run build
```

必须真实通过。

### P1：生产部署能力

目标：面试能讲“这个项目怎么上线”。

任务：

1. 新增后端 `Dockerfile`。
2. 新增前端 `Dockerfile` + Nginx 配置。
3. 新增根目录 `docker-compose.yml`：backend、frontend、mysql、redis、rabbitmq。
4. 新增 `application-prod.yml`：全部通过环境变量注入。
5. 新增健康检查：Spring Boot Actuator `/actuator/health`。
6. 新增部署文档 `docs/deployment.md`。

验收：

```bash
docker compose up -d --build
docker compose ps
curl http://localhost:8080/actuator/health
curl http://localhost/
```

### P2：安全与权限

目标：从“能登录”升级成“像真实系统”。

任务：

1. 用户密码统一 BCrypt，启动或 SQL 初始化时不再保留明文密码。
2. 登录失败计数与账号临时锁定。
3. 角色权限：普通用户/审批人/管理员。
4. 接口级权限控制：审批流管理仅管理员可用。
5. Refresh Token 或 token 自动续期策略。
6. 审计日志：登录、登出、审批、配置变更。
7. 统一 HTTP 状态码：认证失败返回 401，权限不足返回 403。

### P3：审批流引擎深化

目标：把业务复杂度做成核心亮点。

任务：

1. 审批流版本化：旧工单绑定流程快照，避免流程修改影响历史工单。
2. 条件审批：按金额/部门/类型选择不同审批路径。
3. 会签/或签模式：当前“任一通过”扩展为 `ANY` / `ALL`。
4. 委托审批：审批人请假时委托他人处理。
5. 超时自动升级/催办。
6. 状态机化：将状态迁移集中管理，防止散落在 service 中。

### P4：性能与可观测性

目标：能讲性能优化和排查问题。

任务：

1. 接入 Actuator 指标。
2. 日志增加 traceId，前后端请求链路可追踪。
3. 慢接口日志拦截器。
4. Redis 缓存命中率统计。
5. 数据库索引补齐和 explain 文档。
6. JMeter/k6 压测脚本与压测报告。
7. 前端大包拆分：Element Plus、vendor、echarts/图表按需拆包。

### P5：文档与面试材料

目标：让面试官能快速看懂，也让你能讲得清楚。

任务：

1. `docs/architecture.md`：架构图 + 请求链路。
2. `docs/database.md`：表结构、索引、核心 SQL。
3. `docs/api.md`：纯接口文档。
4. `docs/interview.md`：面试讲解稿与问答。
5. `docs/demo-script.md`：5 分钟演示脚本。
6. README 重构：截图、快速启动、亮点、部署、测试覆盖。

---

## 3. 第一阶段建议执行顺序

### Task 1：保护当前工作区

**Objective:** 当前已有大量未提交改动，先避免后续改造混乱。

**Commands:**

```bash
git status --short
git diff --stat
```

**Decision:**

- 如果当前改动都是你要保留的：按主题提交。
- 如果有临时代码：先备份分支。

推荐：

```bash
git checkout -b feature/production-upgrade
```

### Task 2：增加后端测试基础

**Objective:** 让 `mvn test` 真正跑测试，而不是空跑。

**Files:**

- Modify: `backend/pom.xml`
- Create: `backend/src/test/java/com/zmd/order/auth/JwtUtilTest.java`
- Create: `backend/src/test/java/com/zmd/order/auth/AuthControllerPasswordTest.java`

**Verification:**

```bash
cd backend
mvn test
```

### Task 3：生产配置分层

**Objective:** 增加 `prod` profile，避免生产使用 dev 配置。

**Files:**

- Create: `backend/src/main/resources/application-prod.yml`
- Create: `.env.example`
- Modify: `README.md`

**Verification:**

```bash
cd backend
mvn -q -DskipTests package
```

### Task 4：Docker Compose 本地生产演示环境

**Objective:** 一条命令启动全套环境。

**Files:**

- Create: `backend/Dockerfile`
- Create: `frontend/Dockerfile`
- Create: `frontend/nginx.conf`
- Create: `docker-compose.yml`

**Verification:**

```bash
docker compose up -d --build
```

### Task 5：前端构建优化

**Objective:** 解决当前 Vite 大包警告。

**Files:**

- Modify: `frontend/vite.config.js`

**Approach:**

- `manualChunks` 拆分 `vue`、`element-plus`、`vendor`。
- 必要时把图表库、WebSocket 客户端单独拆包。

**Verification:**

```bash
cd frontend
npm run build
```

### Task 6：面试文档第一版

**Objective:** 把项目亮点转化为面试表达。

**Files:**

- Create: `docs/interview.md`
- Create: `docs/demo-script.md`

---

## 4. 面试最值得强化的 8 个亮点

1. **审批流引擎**：多级、多审批人、驳回策略、自动跳过。
2. **并发控制**：Redis 分布式锁防止同一工单并发审批。
3. **缓存体系**：Cache Aside、空值缓存、主动失效、看板缓存。
4. **接口限流**：Redis + Lua 滑动窗口，登录防暴力破解。
5. **异步通知**：RabbitMQ 解耦审批和通知，WebSocket 实时推送。
6. **查询优化**：分页、批量查询、消除 N+1、索引设计。
7. **生产部署**：Docker Compose + Nginx + 环境变量 + 健康检查。
8. **质量保障**：单元测试、集成测试、CI、压测报告。

---

## 5. 不建议优先做的事

- 不要先重构成微服务。当前项目体量适合单体模块化，盲目微服务会增加复杂度但不一定加分。
- 不要先升级 Java 17 / Spring Boot 3。你当前面试目标更需要可运行和可讲清楚；升级可作为后续亮点。
- 不要只堆页面。面试更看重后端设计、工程化、性能和问题排查。
- 不要引入太多中间件。每个中间件都要能讲清楚为什么用、解决什么问题、失败怎么降级。

---

## 6. 下一步推荐

立即从 P0 开始：

1. 建 `feature/production-upgrade` 分支。
2. 整理当前未提交改动。
3. 补后端测试基础。
4. 补生产配置和 Docker Compose。
5. 最后再做前端体验和面试文档。
