# CC-2026-07-07-04 后端收尾（文档补全 + 债项复核）

- 状态：done
- 关联技术债：§7 P2-3
- 创建：2026-07-07
- 修改人：AI

## 背景
后端性能债已在 CC-01（P0-1/P0-2/P1-1/P1-3）、CC-02（A1/A3）大幅收敛。本轮做收尾：补全文档缺口，并复核剩余债项是否仍需代码改动。

## 影响范围
- 文件：`docs/api.md`（补 `/auth/refresh`）
- 接口：无代码变更
- 是否触碰红线：否

## 方案
1. **P2-3 补全 `/auth/refresh`**：登录返回 `refreshToken`、`AuthController` 有 `/auth/refresh` 端点，但 `docs/api.md` 未记录。补端点说明（路径/方法/入参/出参）。
2. **债项复核结论**：
   - P1-2（同单快照反复解析）：当前 `approveOrder` 单请求内仅 `getStep` 解析一次，`enrichOrder` 单路径解析一次；已在 CC-01 批量快照后缓解，标 `[FIXED-缓解]`。
   - P2-1（死字段 `approval_flow_step.approver_id`）：清理需 Flyway 迁移（DB schema 变更，红线），收益低，建议保留并标 `可接受`，不主动动 DB。
   - P2-2（锁忙等 `Thread.sleep(50)`）：AGENTS.md 已记 `可接受`。

## 验证
- 人工核对 `docs/api.md` 与 `AuthController` 实际端点一致。

## 回滚
- 仅文档改动，回退单文件。

## 执行记录（2026-07-07）
- P2-3：`docs/api.md` 在「登录」「登出」之间补全 `POST /auth/refresh`（请求体 `refreshToken`、响应 `data.token`、失败返回业务码 401），与 `AuthController.refresh` 实际实现核对一致。
- 债项复核结论已写入 AGENTS.md §7：P1-2（快照解析）标 `[FIXED-缓解]`（CC-01 批量快照后单请求内仅解析一次）；P2-1（死字段 `approver_id`）保留（清理需 Flyway 迁移，红线，收益低）；P2-2（锁忙等）保留 `可接受`。
- 后端性能债主体已在 CC-01/CC-02 收敛，本轮仅做文档收尾，无代码改动。
