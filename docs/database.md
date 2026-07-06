# 数据库设计文档

## 1. 数据库

| 项 | 值 |
|----|----|
| 数据库 | `order_approval` |
| 字符集 | `utf8mb4` |
| 排序规则 | `utf8mb4_general_ci` |

## 2. 表清单

| 表名 | 说明 |
|------|------|
| `sys_user` | 用户表 |
| `department` | 部门表 |
| `work_order` | 工单表 |
| `approval_flow` | 审批流模板表 |
| `approval_flow_step` | 审批流步骤表 |
| `approval_step_approver` | 步骤审批人表 |
| `approval_record` | 审批记录表 |
| `order_operation_log` | 工单操作日志表 |

## 3. 表结构

### 3.1 `sys_user`

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | `BIGINT` | 主键 |
| `username` | `VARCHAR(50)` | 用户名，唯一 |
| `password` | `VARCHAR(100)` | 密码 |
| `role` | `VARCHAR(20)` | 角色：`ADMIN` / `APPROVER` / `USER` |
| `dept_id` | `BIGINT` | 部门 ID |
| `status` | `TINYINT` | `1` 启用，`0` 禁用 |
| `create_time` | `DATETIME` | 创建时间 |
| `update_time` | `DATETIME` | 更新时间 |

索引：

| 索引 | 字段 |
|------|------|
| `UNIQUE` | `username` |

### 3.2 `department`

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | `BIGINT` | 主键 |
| `name` | `VARCHAR(100)` | 部门名称 |
| `parent_id` | `BIGINT` | 上级部门 ID |
| `create_time` | `DATETIME` | 创建时间 |

### 3.3 `work_order`

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | `BIGINT` | 主键 |
| `title` | `VARCHAR(200)` | 工单标题 |
| `content` | `TEXT` | 工单内容 |
| `status` | `INT` | 状态 |
| `creator_id` | `BIGINT` | 创建人 ID |
| `approver_id` | `BIGINT` | 最后审批人 ID |
| `flow_id` | `BIGINT` | 审批流模板 ID |
| `current_step` | `INT` | 当前步骤序号 |
| `submit_step` | `INT` | 提交步骤序号 |
| `dept_id` | `BIGINT` | 部门 ID |
| `status` | `TINYINT` | `1` 启用，`0` 禁用 |
| `create_time` | `DATETIME` | 创建时间 |
| `update_time` | `DATETIME` | 更新时间 |
| `approve_time` | `DATETIME` | 审批时间 |

状态值：

| 值 | 说明 |
|----|------|
| `0` | 待审批 |
| `1` | 审批中 |
| `2` | 已通过 |
| `3` | 已驳回 |
| `4` | 已关闭 |
| `5` | 退回修改 |

索引：

| 索引 | 字段 |
|------|------|
| `idx_creator` | `creator_id` |
| `idx_status` | `status` |
| `idx_create_time` | `create_time` |

### 3.4 `approval_flow`

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | `BIGINT` | 主键 |
| `name` | `VARCHAR(100)` | 流程名称 |
| `description` | `VARCHAR(500)` | 流程描述 |
| `status` | `TINYINT` | `1` 启用，`0` 禁用 |
| `reject_mode` | `VARCHAR(20)` | 驳回策略 |
| `create_time` | `DATETIME` | 创建时间 |

驳回策略：

| 值 | 说明 |
|----|------|
| `RESTART` | 回到第一步 |
| `PREVIOUS` | 回到上一步 |
| `ORIGIN` | 退回发起人 |

### 3.5 `approval_flow_step`

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | `BIGINT` | 主键 |
| `flow_id` | `BIGINT` | 审批流 ID |
| `step_order` | `INT` | 步骤序号 |
| `step_name` | `VARCHAR(50)` | 步骤名称 |
| `approver_id` | `BIGINT` | 兼容字段 |
| `create_time` | `DATETIME` | 创建时间 |

索引：

| 索引 | 字段 |
|------|------|
| `idx_flow_id` | `flow_id` |

### 3.6 `approval_step_approver`

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | `BIGINT` | 主键 |
| `step_id` | `BIGINT` | 审批步骤 ID |
| `user_id` | `BIGINT` | 审批人 ID |
| `create_time` | `DATETIME` | 创建时间 |

索引：

| 索引 | 字段 |
|------|------|
| `idx_step_id` | `step_id` |
| `idx_user_id` | `user_id` |

### 3.7 `approval_record`

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | `BIGINT` | 主键 |
| `order_id` | `BIGINT` | 工单 ID |
| `step_id` | `BIGINT` | 审批步骤 ID |
| `step_order` | `INT` | 步骤序号 |
| `approver_id` | `BIGINT` | 审批人 ID |
| `result` | `VARCHAR(20)` | `APPROVED` / `REJECTED` |
| `remark` | `VARCHAR(500)` | 审批备注 |
| `operate_time` | `DATETIME` | 操作时间 |

索引：

| 索引 | 字段 |
|------|------|
| `idx_order_id` | `order_id` |

### 3.8 `order_operation_log`

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | `BIGINT` | 主键 |
| `order_id` | `BIGINT` | 工单 ID |
| `operator_id` | `BIGINT` | 操作人 ID |
| `operator_name` | `VARCHAR(50)` | 操作人名称 |
| `operation` | `VARCHAR(50)` | 操作类型 |
| `detail` | `VARCHAR(500)` | 操作详情 |
| `operate_time` | `DATETIME` | 操作时间 |

索引：

| 索引 | 字段 |
|------|------|
| `idx_order_id` | `order_id` |
| `idx_operate_time` | `operate_time` |

## 4. 关系

```text
sys_user.dept_id              → department.id
work_order.creator_id         → sys_user.id
work_order.approver_id        → sys_user.id
work_order.flow_id            → approval_flow.id
approval_flow_step.flow_id    → approval_flow.id
approval_step_approver.step_id → approval_flow_step.id
approval_step_approver.user_id → sys_user.id
approval_record.order_id      → work_order.id
approval_record.step_id       → approval_flow_step.id
approval_record.approver_id   → sys_user.id
order_operation_log.order_id  → work_order.id
order_operation_log.operator_id → sys_user.id
```

## 5. 核心查询

### 5.1 工单分页

```sql
SELECT *
FROM work_order
WHERE status = ?
  AND title LIKE CONCAT('%', ?, '%')
ORDER BY create_time DESC
LIMIT ?, ?;
```

### 5.2 工单审批记录

```sql
SELECT *
FROM approval_record
WHERE order_id = ?
ORDER BY step_order ASC;
```

### 5.3 审批流步骤

```sql
SELECT *
FROM approval_flow_step
WHERE flow_id = ?
ORDER BY step_order ASC;
```

### 5.4 步骤审批人

```sql
SELECT *
FROM approval_step_approver
WHERE step_id IN (?, ?, ?);
```

## 6. 后续索引优化建议

| 表 | 建议索引 | 用途 |
|------|------|------|
| `work_order` | `(status, create_time)` | 状态筛选 + 时间排序 |
| `work_order` | `(flow_id, current_step)` | 当前步骤审批查询 |
| `approval_record` | `(order_id, step_id, approver_id)` | 重复审批校验 |
| `approval_step_approver` | `(step_id, user_id)` | 当前步骤审批人校验 |
| `order_operation_log` | `(order_id, operate_time)` | 工单日志时间线 |
