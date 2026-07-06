# 测试文档

## 1. 测试命令

后端：

```bash
cd backend
mvn -q test
```

前端：

```bash
cd frontend
npm run build
```

Compose 配置：

```bash
docker compose config
```

## 2. 已有后端测试

| 测试类 | 覆盖内容 |
|------|------|
| `JwtUtilTest` | JWT 生成、解析、非法 token、短密钥补齐 |
| `OrderCreateDTOValidationTest` | 创建工单参数校验 |
| `ApprovalDTOValidationTest` | 审批参数校验 |

## 3. 测试文件

```text
backend/src/test/java/com/zmd/order/auth/JwtUtilTest.java
backend/src/test/java/com/zmd/order/dto/OrderCreateDTOValidationTest.java
backend/src/test/java/com/zmd/order/dto/ApprovalDTOValidationTest.java
```

## 4. 当前测试重点

- JWT claim 正确写入。
- 非法 token 返回无效。
- 短 secret 能补齐到 HS256 要求。
- 创建工单标题不能为空。
- 创建工单审批流不能为空。
- 审批工单 ID 不能为空。
- 审批结果不能为空。

## 5. 后续测试计划

| 优先级 | 测试 | 内容 |
|------|------|------|
| P0 | `OrderServiceImplTest` | 创建工单、审批通过、驳回、重新提交 |
| P0 | `ApprovalFlowServiceImplTest` | 流程列表、流程详情、创建流程 |
| P1 | `RateLimitInterceptorTest` | 用户/IP 限流 key、超限响应 |
| P1 | `RedisDistributedLockTest` | 加锁、释放、误删保护 |
| P1 | `AuthInterceptorTest` | token 缺失、过期、Redis 失效 |
| P2 | 集成测试 | 登录 → 创建工单 → 审批完整链路 |

## 6. CI

GitHub Actions 文件：

```text
.github/workflows/ci.yml
```

任务：

- `backend-test`: `mvn -q test`
- `frontend-build`: `npm ci && npm run build`
- `compose-validate`: `docker compose config`
