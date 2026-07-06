# 系统架构文档

## 1. 系统定位

zmd-crm 是一个企业内部工单审批系统，核心能力包括工单创建、多级审批流、多人审批、驳回策略、实时通知、缓存、限流、分布式锁、看板统计和容器化部署。

## 2. 技术架构

```text
Browser
  │
  │ HTTP / WebSocket
  ▼
Nginx + Vue3
  │
  │ /api /auth /ws
  ▼
Spring Boot 2.7
  ├─ AuthInterceptor：JWT 认证
  ├─ RateLimitInterceptor：Redis + Lua 限流
  ├─ Controller：接口入口
  ├─ Service：审批流与工单业务
  ├─ Mapper：MyBatis-Plus 数据访问
  ├─ Redis：token、缓存、锁、限流
  ├─ RabbitMQ：审批通知消息
  └─ WebSocket：实时通知推送
  │
  ▼
MySQL 8
```

## 3. 后端模块

| 模块 | 路径 | 职责 |
|------|------|------|
| 认证 | `auth` | 登录、登出、JWT、登录用户上下文 |
| 缓存 | `cache` | 工单详情缓存、空值缓存、缓存失效 |
| 通用 | `common` | 统一响应、异常、分页、常量 |
| 配置 | `config` | Redis、RabbitMQ、MyBatis-Plus、Web MVC |
| 控制器 | `controller` | REST API |
| DTO | `dto` | 请求参数对象 |
| 实体 | `entity` | 数据库实体与非表字段 |
| 锁 | `lock` | Redis 分布式锁 |
| Mapper | `mapper` | MyBatis-Plus Mapper |
| MQ | `mq` | 审批通知生产与消费 |
| 限流 | `rate` | 注解限流与拦截器 |
| 定时任务 | `schedule` | 超时工单处理 |
| 服务 | `service` | 工单、用户、审批流、看板业务 |
| WebSocket | `websocket` | 实时通知 |

## 4. 前端模块

| 模块 | 路径 | 职责 |
|------|------|------|
| API | `frontend/src/api` | 后端接口封装 |
| 布局 | `frontend/src/components/Layout.vue` | 主布局与菜单 |
| 路由 | `frontend/src/router` | 页面路由与登录守卫 |
| 工具 | `frontend/src/utils` | 请求、认证、WebSocket |
| 页面 | `frontend/src/views` | 登录、看板、工单、审批流、用户管理页面 |

## 5. 核心链路

### 5.1 登录链路

```text
Login.vue
  → POST /auth/login
  → AuthController.login
  → UserMapper.selectOne
  → JwtUtil.generateToken
  → Redis 写入 token TTL
  → 返回 token/userId/username/role
  → 前端保存 token 与用户信息
```

### 5.2 创建工单链路

```text
OrderCreate.vue
  → POST /api/order/create
  → AuthInterceptor 校验 token
  → RateLimitInterceptor 执行限流
  → OrderController.create
  → OrderServiceImpl.createOrder
  → RedisDistributedLock 加锁
  → work_order 插入数据
  → order_operation_log 写操作日志
  → 释放分布式锁
```

### 5.3 审批链路

```text
OrderDetail.vue / TodoList.vue
  → POST /api/order/approve
  → AuthInterceptor 校验 token
  → OrderController.approve
  → OrderServiceImpl.approveOrder
  → RedisDistributedLock 按 orderId 加锁
  → 校验审批权限
  → 写 approval_record
  → 更新 work_order 状态/步骤
  → 删除工单缓存与看板缓存
  → RabbitMQ 发送审批通知
  → WebSocket 推送给创建人
```

### 5.4 看板链路

```text
Dashboard.vue
  → GET /api/dashboard/stats
  → DashboardServiceImpl.getStats
  → 先查 Redis 缓存
  → 未命中则聚合 MySQL 数据
  → 写入 Redis，TTL 5 分钟
  → 返回统计数据
```

## 6. 中间件职责

| 中间件 | 使用点 | 数据 |
|------|------|------|
| MySQL | 业务持久化 | 用户、部门、工单、审批流、审批记录、操作日志 |
| Redis | 缓存/锁/限流/token | `order:token:*`、`order:cache:detail:*`、`order:lock:*`、`order:rate:*` |
| RabbitMQ | 异步通知 | `order.exchange`、`order.approval.queue` |
| WebSocket | 实时推送 | `/topic/notifications/{userId}` |
| Nginx | 静态资源与反向代理 | `/api`、`/auth`、`/ws` |

## 7. 部署架构

```text
Docker Compose
  ├─ frontend: Nginx + Vue dist
  ├─ backend: Spring Boot jar
  ├─ mysql: MySQL 8
  ├─ redis: Redis 7
  └─ rabbitmq: RabbitMQ Management
```

| 服务 | 端口 |
|------|------|
| 前端 | `80` |
| 后端 | `8080` |
| MySQL | `3306` |
| Redis | `6379` |
| RabbitMQ | `5672` |
| RabbitMQ 管理台 | `15672` |

## 8. 生产级扩展方向

| 方向 | 当前状态 | 后续升级 |
|------|------|------|
| 测试 | 已有 JWT/DTO 单测 | 补服务层与集成测试 |
| 配置 | 已有 `prod` profile | 引入配置中心 |
| 部署 | 已有 Docker Compose | 接入 Jenkins/GitHub Actions 发布 |
| 监控 | 已有 Actuator | 接 Prometheus/Grafana |
| 日志 | 文件日志 | traceId、慢接口日志、ELK |
| 权限 | JWT 认证 | RBAC 接口权限 |
| 审批流 | 多级/多人/驳回 | 流程版本、条件审批、会签 |
