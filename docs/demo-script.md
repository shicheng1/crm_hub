# 项目演示脚本

## 1. 演示目标

用 5 分钟展示项目不是简单 CRUD，而是具备审批流、并发控制、缓存、限流、消息通知和生产部署能力的企业级项目。

## 2. 演示前准备

```bash
cd D:/07-person/zmd-crm
```

本地开发启动：

```bash
cd backend
mvn spring-boot:run
```

```bash
cd frontend
npm run dev
```

生产化演示启动：

```bash
cp .env.example .env
docker compose up -d --build
```

健康检查：

```bash
curl http://localhost:8080/actuator/health
```

## 3. 演示账号

| 用户 | 密码 | 角色 |
|------|------|------|
| `admin` | `123456` | 审批人 |
| `user1` | `123456` | 普通用户 |
| `user2` | `123456` | 普通用户 |

## 4. 5 分钟演示流程

### 第 1 分钟：项目总览

打开 README，说明项目定位：

```text
企业内部工单审批系统，支持动态审批流、多级审批、多审批人、驳回策略、实时通知、Redis 缓存、分布式锁、限流、RabbitMQ、WebSocket 和 Docker Compose 部署。
```

展示目录：

```text
backend/src/main/java/com/zmd/order
frontend/src
``` 

### 第 2 分钟：登录和创建工单

1. 使用 `user1 / 123456` 登录。
2. 进入创建工单页面。
3. 选择审批流。
4. 填写标题和内容。
5. 提交工单。

讲解点：

- 登录后后端生成 JWT。
- token 写入 Redis，实现主动失效。
- 创建工单接口使用 `@RateLimit`。
- 创建工单时使用 Redis 分布式锁防重复提交。

### 第 3 分钟：审批流转

1. 使用 `admin / 123456` 登录。
2. 进入待办列表。
3. 打开工单详情。
4. 点击通过或驳回。
5. 查看审批记录和操作日志。

讲解点：

- 审批流不是写死的，由 `approval_flow`、`approval_flow_step`、`approval_step_approver` 配置。
- 工单通过 `currentStep` 记录当前步骤。
- 审批记录写入 `approval_record`。
- 操作日志写入 `order_operation_log`。
- 同一工单审批时按 `orderId` 加分布式锁。

### 第 4 分钟：通知和看板

1. 展示看板统计。
2. 展示审批后数据变化。
3. 展示 WebSocket 通知。

讲解点：

- 看板统计缓存到 Redis，5 分钟过期。
- 审批后主动清理看板缓存。
- RabbitMQ 解耦审批和通知。
- WebSocket 推送到 `/topic/notifications/{userId}`。

### 第 5 分钟：工程化能力

展示这些文件：

```text
backend/src/main/resources/application-prod.yml
docker-compose.yml
frontend/nginx.conf
.github/workflows/ci.yml
docs/deployment.md
```

讲解点：

- 生产配置通过环境变量注入。
- Docker Compose 一键启动 MySQL、Redis、RabbitMQ、后端、前端。
- Nginx 托管 Vue 静态资源并代理 API/WebSocket。
- Actuator 提供健康检查。
- GitHub Actions 做后端测试、前端构建、Compose 校验。

## 5. 演示命令

后端测试：

```bash
cd backend
mvn -q test
```

前端构建：

```bash
cd frontend
npm run build
```

Compose 校验：

```bash
docker compose config
```

Git 提交记录：

```bash
git log --oneline -5
```

## 6. 面试收尾话术

```text
这个项目我重点不是做页面，而是把审批流、Redis、MQ、WebSocket、Docker 和测试这些企业项目常见能力串起来。后续我还会继续做审批流版本化、RBAC 权限、traceId 日志链路、Prometheus 指标和压测报告。
```
