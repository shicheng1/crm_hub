# 故障排查文档

## 1. 后端启动失败

### 1.1 数据库连接失败

检查：

```bash
docker compose ps mysql
```

查看日志：

```bash
docker compose logs -f mysql
docker compose logs -f backend
```

常见原因：

- MySQL 未启动。
- `MYSQL_URL` 主机名错误。
- 用户名或密码错误。
- 数据库初始化失败。

### 1.2 Redis 连接失败

检查：

```bash
docker compose ps redis
docker compose logs -f redis
```

常见原因：

- Redis 未启动。
- `REDIS_HOST` 配置错误。
- Redis 密码配置不一致。

### 1.3 RabbitMQ 连接失败

检查：

```bash
docker compose ps rabbitmq
docker compose logs -f rabbitmq
```

常见原因：

- RabbitMQ 未启动。
- 用户名或密码错误。
- vhost 配置错误。

## 2. 前端访问 404

检查 Nginx 配置：

```bash
docker compose logs -f frontend
```

关键配置：

```nginx
try_files $uri $uri/ /index.html;
```

## 3. API 请求失败

检查代理路径：

```nginx
location /api/
location /auth/
```

检查后端健康：

```bash
curl http://localhost:8080/actuator/health
curl http://localhost/actuator/health
```

## 4. WebSocket 连接失败

检查 Nginx WebSocket 代理：

```nginx
proxy_http_version 1.1;
proxy_set_header Upgrade $http_upgrade;
proxy_set_header Connection "upgrade";
```

检查前端订阅路径：

```text
/topic/notifications/{userId}
```

## 5. 登录成功后接口仍返回未登录

检查：

- 前端是否写入 token。
- 请求 Header 是否带 `Authorization: Bearer {token}`。
- Redis 中 token key 是否存在。
- JWT_SECRET 是否和签发时一致。

Redis key：

```text
order:token:{token}
```

## 6. 审批失败

常见返回：

| 信息 | 可能原因 |
|------|------|
| `工单不存在` | 工单 ID 错误 |
| `工单状态不允许审批` | 工单已通过、关闭或退回 |
| `您不是当前步骤的审批人` | 当前登录用户不在步骤审批人列表 |
| `您已经审批过该步骤` | 重复审批 |
| `该工单正在审批中，请稍后再试` | 分布式锁未获取成功 |

## 7. Docker Compose 重建

重建服务：

```bash
docker compose up -d --build
```

清理数据后重建：

```bash
docker compose down -v
docker compose up -d --build
```

## 8. 查看日志

```bash
docker compose logs -f backend
docker compose logs -f frontend
docker compose logs -f mysql
docker compose logs -f redis
docker compose logs -f rabbitmq
```

## 9. 本地验证

```bash
cd backend && mvn -q test
cd frontend && npm run build
docker compose config
```
