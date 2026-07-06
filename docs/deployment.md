# zmd-crm 部署说明

## 本地生产化演示环境

### 1. 准备环境变量

```bash
cp .env.example .env
```

至少修改：

- `MYSQL_ROOT_PASSWORD`
- `MYSQL_PASSWORD`
- `RABBITMQ_PASSWORD`
- `JWT_SECRET`

`JWT_SECRET` 生产环境必须使用至少 32 字节随机字符串。

### 2. 一键启动

```bash
docker compose up -d --build
```

启动服务：

| 服务 | 地址 |
|---|---|
| 前端 | http://localhost |
| 后端 API | http://localhost:8080 |
| 健康检查 | http://localhost:8080/actuator/health |
| RabbitMQ 管理台 | http://localhost:15672 |
| MySQL | localhost:3306 |
| Redis | localhost:6379 |

### 3. 查看状态

```bash
docker compose ps
docker compose logs -f backend
docker compose logs -f frontend
```

### 4. 健康检查

```bash
curl http://localhost:8080/actuator/health
curl http://localhost/actuator/health
```

### 5. 停止环境

```bash
docker compose down
```

如需清空数据库和中间件数据：

```bash
docker compose down -v
```

## 生产配置说明

后端生产配置位于：

```text
backend/src/main/resources/application-prod.yml
```

核心原则：

- 数据库、Redis、RabbitMQ、JWT 密钥全部通过环境变量注入。
- 开启 Actuator 健康检查、info、metrics。
- 日志写入 `/opt/app/logs/order-approval.log`。
- MyBatis SQL 日志在生产环境使用 Slf4j，不直接输出 stdout。

## 面试可讲点

1. 使用 Docker Compose 编排 MySQL、Redis、RabbitMQ、后端、前端。
2. 前端用 Nginx 托管静态资源并反向代理 API / WebSocket。
3. 后端使用 `prod` profile 隔离生产配置。
4. 使用 Actuator 提供健康检查，便于容器编排平台探活。
5. 数据库初始化脚本挂载到 MySQL `/docker-entrypoint-initdb.d/`，首次启动自动建表和灌入测试数据。
