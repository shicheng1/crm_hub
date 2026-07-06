# 安全文档

## 1. 认证

| 项 | 实现 |
|----|------|
| 登录接口 | `POST /auth/login` |
| token 类型 | JWT |
| token 存储 | Redis |
| Header | `Authorization: Bearer {token}` |
| 登出接口 | `POST /auth/logout` |

## 2. JWT

JWT 载荷字段：

| 字段 | 说明 |
|------|------|
| `userId` | 用户 ID |
| `username` | 用户名 |
| `role` | 用户角色 |
| `iat` | 签发时间 |
| `exp` | 过期时间 |

配置项：

| 配置 | 说明 |
|------|------|
| `jwt.secret` | 签名密钥 |
| `jwt.expire-hours` | 过期时间 |

生产环境通过环境变量注入：

```text
JWT_SECRET
JWT_EXPIRE_HOURS
```

## 3. Redis token 主动失效

Redis key：

```text
order:token:{token}
```

登录成功：写入 Redis，TTL 与 JWT 过期时间一致。

登出：删除 Redis token key。

接口请求：校验 JWT 有效性，同时校验 Redis token key 是否存在。

## 4. 密码

当前支持：

- BCrypt 密码校验。
- 兼容初始化 SQL 中的明文密码。

后续生产升级：

- 初始化数据使用 BCrypt 密文。
- 登录成功后自动升级明文密码为 BCrypt。
- 增加登录失败次数限制和账号锁定。

## 5. 接口限流

限流注解：

```java
@RateLimit(key = "auth:login", maxCount = 5, windowSeconds = 60)
```

Redis key：

```text
order:rate:{key}:{identity}
```

identity：

| 场景 | identity |
|------|----------|
| 已登录 | `u{userId}` |
| 未登录 | `ip{clientIp}` |

已接入接口：

| 接口 | 限流 |
|------|------|
| `POST /auth/login` | 60 秒 5 次 |
| `POST /api/order/create` | 60 秒 10 次 |

## 6. 分布式锁

Redis key：

| key | 场景 |
|------|------|
| `order:lock:create:{userId}` | 创建工单防重复提交 |
| `order:lock:approve:{orderId}` | 审批工单防并发修改 |

实现：

- 加锁：`SET key value NX EX seconds`
- 解锁：Lua 脚本判断 value 后删除

## 7. 敏感配置

生产配置不写死在代码中，通过环境变量注入：

| 环境变量 | 说明 |
|------|------|
| `MYSQL_PASSWORD` | MySQL 密码 |
| `REDIS_PASSWORD` | Redis 密码 |
| `RABBITMQ_PASSWORD` | RabbitMQ 密码 |
| `JWT_SECRET` | JWT 签名密钥 |

## 8. 后续安全增强

| 项 | 目标 |
|----|------|
| RBAC | 管理员、审批人、普通用户接口权限隔离 |
| Refresh Token | token 自动续期与刷新 token 轮换 |
| 审计日志 | 登录、登出、审批、配置变更全记录 |
| 密码升级 | 初始化密码全部 BCrypt |
| 登录保护 | 失败次数限制、账号临时锁定 |
| CORS | 生产域名白名单 |
| HTTP 状态码 | 认证失败 401，权限不足 403 |
