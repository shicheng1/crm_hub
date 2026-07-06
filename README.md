# 工单审批系统（Order Approval System）

一个面向企业内部的工单审批系统，支持多级审批流、多审批人、驳回策略、实时通知等特性。项目采用 Spring Boot + Vue3 全栈技术栈，覆盖后端服务、前端界面、消息队列、缓存、分布式锁等常见企业级技术点。

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
├── backend/                          # 后端服务
│   ├── src/main/java/com/zmd/order/
│   │   ├── OrderApplication.java     # 启动类
│   │   ├── auth/                     # 认证（JWT、拦截器、登录）
│   │   ├── cache/                    # Redis 缓存服务
│   │   ├── common/                   # 常量、统一返回、异常处理
│   │   ├── config/                   # 配置（Redis、RabbitMQ、MyBatis、WebMvc）
│   │   ├── controller/               # 控制器
│   │   ├── dto/                      # 数据传输对象
│   │   ├── entity/                   # 实体类
│   │   ├── lock/                     # 分布式锁
│   │   ├── mapper/                   # MyBatis Mapper
│   │   ├── mq/                       # RabbitMQ 生产者/消费者
│   │   ├── rate/                     # 限流
│   │   ├── schedule/                 # 定时任务
│   │   ├── service/                  # 业务服务
│   │   └── websocket/                # WebSocket 通知
│   └── src/main/resources/
│       ├── db/                       # SQL 脚本（v1/v2/v3）
│       ├── lua/                      # Lua 脚本（锁释放、限流）
│       └── application*.yml          # 配置文件
├── frontend/                         # 前端
│   └── src/
│       ├── api/                      # API 请求
│       ├── components/               # 组件（Layout）
│       ├── router/                   # 路由
│       ├── utils/                    # 工具（请求、认证、WebSocket）
│       └── views/                    # 页面
└── README.md
```

## 快速开始

### 环境要求
- JDK 8+
- MySQL 8+
- Redis 5+
- RabbitMQ 3.8+（可选，可通过 `mq.enabled=false` 关闭）
- Node.js 16+

### 1. 初始化数据库
```bash
# 按顺序执行 SQL 脚本
mysql -u root -p < backend/src/main/resources/db/init.sql
mysql -u root -p order_approval < backend/src/main/resources/db/v2_add_approval_flow.sql
mysql -u root -p order_approval < backend/src/main/resources/db/v3_multi_approver.sql
```

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

## 文档

| 文档 | 路径 |
|------|------|
| API 接口文档 | `docs/api.md` |
| 架构文档 | `docs/architecture.md` |
| 数据库设计 | `docs/database.md` |
| 部署说明 | `docs/deployment.md` |
| 用户管理需求 | `docs/user-management.md` |
| 面试讲解稿 | `docs/interview.md` |
| 演示脚本 | `docs/demo-script.md` |
| 安全文档 | `docs/security.md` |
| 性能优化 | `docs/performance.md` |
| 测试说明 | `docs/testing.md` |
| 故障排查 | `docs/troubleshooting.md` |
| 生产级升级路线图 | `docs/plans/production-upgrade-roadmap.md` |

## 技术亮点（面试重点）

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

### 6. SQL 优化（消除 N+1）
- 待办/已办列表：JOIN 查询一次性查出
- 批量加载审批人、创建人姓名
- MyBatis-Plus 分页插件配置

## 面试常见问答

**Q: 为什么用分布式锁？不用行锁？**
A: 行锁只能保证单库单表内的并发安全，分布式锁可以跨服务实例。本项目中即使部署多个后端实例，分布式锁依然能防止同一工单被并发审批。

**Q: 缓存和数据库一致性怎么保证？**
A: 采用 Cache Aside Pattern（先更新数据库，再删除缓存）。极端并发场景下可能出现短暂不一致，但工单审批是低频操作，可接受。如需强一致可用延迟双删或消息队列同步。

**Q: JWT 为什么还要存 Redis？**
A: JWT 本身是无状态的，无法主动注销。存 Redis 后，注销时删除 Redis 中的 token，拦截器校验时检查 Redis 是否存在，实现主动注销和 token 失效。

**Q: RabbitMQ 消息丢失怎么处理？**
A: 1) 生产者开启确认模式（CorrelationData）；2) 队列持久化（durable）；3) 消费者手动 ACK，业务成功后才确认；4) 消费失败 nack 不重新入队，避免无限重试，配合死信队列处理。

**Q: 相同审批人跳过逻辑怎么实现的？**
A: 当前步骤通过后，查找下一步；如果下一步的审批人列表包含当前审批人，且该审批人在下一步尚未审批过，则自动跳过，继续查找下下步，直到遇到不同审批人或流程结束。

## 配置说明

### 环境变量（生产环境推荐）
| 变量名 | 说明 | 默认值 |
|--------|------|--------|
| JWT_SECRET | JWT 签名密钥 | zmd-order-approval-secret-key-2024-please-change-in-prod |
| JWT_EXPIRE_HOURS | JWT 过期时间（小时） | 24 |

### MQ 开关
`application-dev.yml` 中 `mq.enabled=true` 启用 RabbitMQ，设为 `false` 时通知降级为同步 WebSocket 推送。

## License
MIT
