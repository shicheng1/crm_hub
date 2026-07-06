# 性能与优化文档

## 1. 当前优化点

| 方向 | 实现 |
|------|------|
| 缓存 | Redis 缓存工单详情和看板统计 |
| 防穿透 | 工单不存在时写空值缓存 |
| 并发控制 | Redis 分布式锁保护创建与审批 |
| 限流 | Redis + Lua 滑动窗口 |
| SQL | 分页查询、批量查询、JOIN 查询 |
| 前端 | Vite manualChunks 拆分业务包、Vue、Element、WebSocket、vendor |

## 2. Redis 缓存

### 2.1 工单详情缓存

key：

```text
order:cache:detail:{orderId}
```

策略：

- 查询详情：先查 Redis，未命中查 MySQL。
- 工单不存在：写入空值缓存。
- 审批或重新提交：删除工单详情缓存。

### 2.2 看板缓存

key：

```text
dashboard:stats
dashboard:trend
```

策略：

- TTL：5 分钟。
- 审批后主动失效。

## 3. 限流

实现：

- `@RateLimit` 标注接口。
- 拦截器读取注解。
- Redis Lua 脚本实现滑动窗口。
- 已登录按用户 ID，未登录按 IP。

已接入：

| 接口 | 窗口 |
|------|------|
| 登录 | 60 秒 5 次 |
| 创建工单 | 60 秒 10 次 |

## 4. SQL 优化

### 4.1 已做

| 场景 | 优化 |
|------|------|
| 工单分页 | MyBatis-Plus 分页 |
| 工单列表创建人 | 批量查询用户 |
| 审批流列表 | 批量查询步骤和审批人 |
| 待办/已办 | Mapper JOIN 查询 |

### 4.2 建议索引

| 表 | 索引 |
|------|------|
| `work_order` | `(status, create_time)` |
| `work_order` | `(flow_id, current_step)` |
| `approval_record` | `(order_id, step_id, approver_id)` |
| `approval_step_approver` | `(step_id, user_id)` |
| `order_operation_log` | `(order_id, operate_time)` |

## 5. 前端构建优化

当前拆分结果：

| chunk | 说明 |
|------|------|
| `index` | 主业务入口 |
| `vue` | Vue / Vue Router |
| `element` | Element Plus |
| `websocket` | STOMP / SockJS |
| `vendor` | Axios |

后续优化：

- Element Plus 按需导入。
- 图标按需注册。
- 路由级异步组件已经使用。
- 大型图表库单独拆包。

## 6. 压测计划

### 6.1 登录接口

目标：验证登录限流和 Redis token 写入。

指标：

- 平均响应时间
- P95/P99
- 错误率
- 限流命中数量

### 6.2 工单列表

目标：验证分页查询和索引效果。

指标：

- QPS
- P95/P99
- MySQL 慢查询
- CPU/内存

### 6.3 审批接口

目标：验证并发审批下的分布式锁效果。

指标：

- 成功审批数
- 锁冲突数
- 重复审批数
- 数据一致性

## 7. 后续可观测性

| 项 | 方案 |
|----|------|
| 请求链路 | traceId MDC |
| 慢接口 | HandlerInterceptor 统计耗时 |
| JVM 指标 | Actuator metrics |
| 业务指标 | 审批耗时、审批通过率 |
| 日志收集 | Loki/ELK |
| 监控面板 | Prometheus + Grafana |
