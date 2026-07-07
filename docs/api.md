# 工单审批系统 API 文档

## 统一约定

- Base URL: `/`
- 认证 Header: `Authorization: Bearer {token}`
- 时间格式: `yyyy-MM-dd HH:mm:ss`
- 统一返回结构:

| 字段 | 说明 |
|------|------|
| `code` | 状态码，`200` 表示成功 |
| `msg` | 返回消息 |
| `data` | 响应数据 |

```json
{
  "code": 200,
  "msg": "success",
  "data": {}
}
```

## 1) 登录

### `POST /auth/login`

**请求体**

| 参数 | 必填 | 说明 |
|------|------|------|
| `username` | 是 | 用户名 |
| `password` | 是 | 密码 |

```json
{
  "username": "admin",
  "password": "123456"
}
```

**响应字段**

| 字段 | 说明 |
|------|------|
| `token` | JWT token |
| `userId` | 用户 ID |
| `username` | 用户名 |
| `role` | 用户角色 |

```json
{
  "code": 200,
  "msg": "success",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiJ9.eyJ1c2VySWQiOjEsInVzZXJuYW1lIjoiYWRtaW4iLCJyb2xlIjoiQVBQUk9WRVIifQ.signature",
    "userId": 1,
    "username": "admin",
    "role": "APPROVER"
  }
}
```

## 刷新 Token（/auth/refresh）

> 登录响应中的 `refreshToken` 用于续期。当前端 access token 过期（响应业务码 `401`）时，`request.js` 自动携带 `refreshToken` 调用本接口换取新 token；刷新失败则跳转登录页。

### `POST /auth/refresh`

**请求体**

| 参数 | 必填 | 说明 |
|------|------|------|
| `refreshToken` | 是 | 登录时返回的 refreshToken |

**响应字段（成功）**

| 字段 | 说明 |
|------|------|
| `token` | 新的 JWT access token |

> 失败时返回业务码 `401`（`code: 401`，非 HTTP 401）及 `msg`，例如 `refreshToken 已过期` / `refreshToken 已失效`。

```json
{
  "code": 200,
  "msg": "success",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiJ9.eyJ1c2VySWQiOjEsInVzZXJuYW1lIjoiYWRtaW4iLCJyb2xlIjoiQVBQUk9WRVIifQ.newSignature"
  }
}
```

## 2) 登出

### `POST /auth/logout`

**Header**

| 参数 | 必填 | 说明 |
|------|------|------|
| `Authorization` | 是 | `Bearer {token}` |

**响应字段**

| 字段 | 说明 |
|------|------|
| `data` | 固定为 `null` |

```json
{
  "code": 200,
  "msg": "success",
  "data": null
}
```

## 3) 创建工单

### `POST /api/order/create`

**请求体**

| 参数 | 必填 | 说明 |
|------|------|------|
| `title` | 是 | 工单标题 |
| `content` | 否 | 工单内容 |
| `flowId` | 是 | 审批流 ID |

```json
{
  "title": "采购申请",
  "content": "采购 2 台开发笔记本",
  "flowId": 1
}
```

**响应字段**

| 字段 | 说明 |
|------|------|
| `data` | 工单 ID |

```json
{
  "code": 200,
  "msg": "success",
  "data": 1001
}
```

## 4) 工单分页

### `GET /api/order/page`

**Query 参数**

| 参数 | 必填 | 说明 |
|------|------|------|
| `page` | 否 | 页码，默认 `1` |
| `size` | 否 | 每页数量，默认 `10` |
| `status` | 否 | 工单状态：`0` 待审批，`1` 审批中，`2` 已通过，`3` 已驳回，`4` 已关闭，`5` 退回修改 |
| `title` | 否 | 标题关键字 |

**响应字段**

| 字段 | 说明 |
|------|------|
| `records` | 工单列表 |
| `total` | 总条数 |
| `size` | 每页数量 |
| `current` | 当前页码 |
| `pages` | 总页数 |

```json
{
  "code": 200,
  "msg": "success",
  "data": {
    "records": [
      {
        "id": 1001,
        "title": "采购申请",
        "content": "采购 2 台开发笔记本",
        "status": 1,
        "creatorId": 2,
        "approverId": null,
        "flowId": 1,
        "currentStep": 1,
        "submitStep": 1,
        "deptId": 1,
        "createTime": "2026-07-06 09:30:00",
        "updateTime": "2026-07-06 09:30:00",
        "approveTime": null,
        "creatorName": "user1",
        "flowName": "普通工单审批",
        "rejectMode": "ORIGIN"
      }
    ],
    "total": 1,
    "size": 10,
    "current": 1,
    "pages": 1
  }
}
```

## 5) 工单详情

### `GET /api/order/detail/{id}`

**Path 参数**

| 参数 | 必填 | 说明 |
|------|------|------|
| `id` | 是 | 工单 ID |

**响应字段**

| 字段 | 说明 |
|------|------|
| `id` | 工单 ID |
| `title` | 工单标题 |
| `content` | 工单内容 |
| `status` | 工单状态 |
| `creatorId` | 创建人 ID |
| `flowId` | 审批流 ID |
| `currentStep` | 当前审批步骤序号 |
| `submitStep` | 提交步骤序号 |
| `flowSteps` | 审批步骤列表 |
| `records` | 审批记录列表 |
| `creatorName` | 创建人名称 |
| `flowName` | 审批流名称 |
| `rejectMode` | 驳回策略 |

```json
{
  "code": 200,
  "msg": "success",
  "data": {
    "id": 1001,
    "title": "采购申请",
    "content": "采购 2 台开发笔记本",
    "status": 1,
    "creatorId": 2,
    "approverId": null,
    "flowId": 1,
    "currentStep": 1,
    "submitStep": 1,
    "deptId": 1,
    "createTime": "2026-07-06 09:30:00",
    "updateTime": "2026-07-06 09:30:00",
    "approveTime": null,
    "flowSteps": [
      {
        "id": 1,
        "flowId": 1,
        "stepOrder": 1,
        "stepName": "部门主管审批",
        "approverId": null,
        "createTime": "2026-07-06 09:00:00",
        "approvers": [
          {
            "id": 1,
            "stepId": 1,
            "userId": 1,
            "createTime": "2026-07-06 09:00:00"
          }
        ]
      }
    ],
    "records": [],
    "creatorName": "user1",
    "flowName": "普通工单审批",
    "rejectMode": "ORIGIN"
  }
}
```

## 6) 审批工单

### `POST /api/order/approve`

**请求体**

| 参数 | 必填 | 说明 |
|------|------|------|
| `orderId` | 是 | 工单 ID |
| `approved` | 是 | `true` 通过，`false` 驳回 |
| `remark` | 否 | 审批备注 |

```json
{
  "orderId": 1001,
  "approved": true,
  "remark": "同意"
}
```

**响应字段**

| 字段 | 说明 |
|------|------|
| `data` | 固定为 `null` |

```json
{
  "code": 200,
  "msg": "success",
  "data": null
}
```

## 7) 重新提交工单

### `POST /api/order/resubmit/{id}`

**Path 参数**

| 参数 | 必填 | 说明 |
|------|------|------|
| `id` | 是 | 工单 ID |

**响应字段**

| 字段 | 说明 |
|------|------|
| `data` | 固定为 `null` |

```json
{
  "code": 200,
  "msg": "success",
  "data": null
}
```

## 8) 待办工单

### `GET /api/order/todo`

**Query 参数**

| 参数 | 必填 | 说明 |
|------|------|------|
| `page` | 否 | 页码，默认 `1` |
| `size` | 否 | 每页数量，默认 `10` |

**响应字段**

| 字段 | 说明 |
|------|------|
| `records` | 待办工单列表 |
| `total` | 总条数 |
| `size` | 每页数量 |
| `current` | 当前页码 |
| `pages` | 总页数 |

```json
{
  "code": 200,
  "msg": "success",
  "data": {
    "records": [
      {
        "id": 1001,
        "title": "采购申请",
        "content": "采购 2 台开发笔记本",
        "status": 1,
        "creatorId": 2,
        "approverId": null,
        "flowId": 1,
        "currentStep": 1,
        "submitStep": 1,
        "deptId": 1,
        "createTime": "2026-07-06 09:30:00",
        "updateTime": "2026-07-06 09:30:00",
        "approveTime": null,
        "creatorName": "user1",
        "flowName": "普通工单审批",
        "rejectMode": "ORIGIN"
      }
    ],
    "total": 1,
    "size": 10,
    "current": 1,
    "pages": 1
  }
}
```

## 9) 已办工单

### `GET /api/order/done`

**Query 参数**

| 参数 | 必填 | 说明 |
|------|------|------|
| `page` | 否 | 页码，默认 `1` |
| `size` | 否 | 每页数量，默认 `10` |

**响应字段**

| 字段 | 说明 |
|------|------|
| `records` | 已办工单列表 |
| `total` | 总条数 |
| `size` | 每页数量 |
| `current` | 当前页码 |
| `pages` | 总页数 |

```json
{
  "code": 200,
  "msg": "success",
  "data": {
    "records": [
      {
        "id": 1002,
        "title": "权限申请",
        "content": "申请生产日志查询权限",
        "status": 2,
        "creatorId": 3,
        "approverId": 1,
        "flowId": 2,
        "currentStep": 1,
        "submitStep": 1,
        "deptId": 1,
        "createTime": "2026-07-05 14:20:00",
        "updateTime": "2026-07-05 14:30:00",
        "approveTime": "2026-07-05 14:30:00",
        "creatorName": "user2",
        "flowName": "快速审批",
        "rejectMode": "PREVIOUS"
      }
    ],
    "total": 1,
    "size": 10,
    "current": 1,
    "pages": 1
  }
}
```

## 10) 工单操作日志

### `GET /api/order/logs/{orderId}`

**Path 参数**

| 参数 | 必填 | 说明 |
|------|------|------|
| `orderId` | 是 | 工单 ID |

**响应字段**

| 字段 | 说明 |
|------|------|
| `id` | 日志 ID |
| `orderId` | 工单 ID |
| `operatorId` | 操作人 ID |
| `operatorName` | 操作人名称 |
| `operation` | 操作类型 |
| `detail` | 操作详情 |
| `operateTime` | 操作时间 |

```json
{
  "code": 200,
  "msg": "success",
  "data": [
    {
      "id": 1,
      "orderId": 1001,
      "operatorId": 2,
      "operatorName": "user1",
      "operation": "CREATE",
      "detail": "创建工单",
      "operateTime": "2026-07-06 09:30:00"
    }
  ]
}
```

## 11) 审批流列表

### `GET /api/flow/list`

**响应字段**

| 字段 | 说明 |
|------|------|
| `id` | 审批流 ID |
| `name` | 审批流名称 |
| `description` | 审批流描述 |
| `status` | 状态 |
| `rejectMode` | 驳回策略 |
| `steps` | 审批步骤列表 |

```json
{
  "code": 200,
  "msg": "success",
  "data": [
    {
      "id": 1,
      "name": "普通工单审批",
      "description": "部门主管审批 → 管理层审批",
      "status": 1,
      "rejectMode": "ORIGIN",
      "createTime": "2026-07-06 09:00:00",
      "steps": [
        {
          "id": 1,
          "flowId": 1,
          "stepOrder": 1,
          "stepName": "部门主管审批",
          "approverId": null,
          "createTime": "2026-07-06 09:00:00",
          "approvers": [
            {
              "id": 1,
              "stepId": 1,
              "userId": 1,
              "createTime": "2026-07-06 09:00:00"
            }
          ]
        }
      ]
    }
  ]
}
```

## 12) 审批流详情

### `GET /api/flow/detail/{id}`

**Path 参数**

| 参数 | 必填 | 说明 |
|------|------|------|
| `id` | 是 | 审批流 ID |

**响应字段**

| 字段 | 说明 |
|------|------|
| `id` | 审批流 ID |
| `name` | 审批流名称 |
| `description` | 审批流描述 |
| `status` | 状态 |
| `rejectMode` | 驳回策略 |
| `steps` | 审批步骤列表 |

```json
{
  "code": 200,
  "msg": "success",
  "data": {
    "id": 2,
    "name": "快速审批",
    "description": "单级审批，admin直接处理",
    "status": 1,
    "rejectMode": "PREVIOUS",
    "createTime": "2026-07-06 09:00:00",
    "steps": [
      {
        "id": 3,
        "flowId": 2,
        "stepOrder": 1,
        "stepName": "直接审批",
        "approverId": 1,
        "createTime": "2026-07-06 09:00:00",
        "approvers": [
          {
            "id": 2,
            "stepId": 3,
            "userId": 1,
            "createTime": "2026-07-06 09:00:00"
          }
        ]
      }
    ]
  }
}
```

## 13) 创建审批流

### `POST /api/flow/create`

**请求体**

| 参数 | 必填 | 说明 |
|------|------|------|
| `name` | 是 | 审批流名称 |
| `description` | 否 | 审批流描述 |
| `status` | 否 | 状态：`1` 启用，`0` 禁用 |
| `rejectMode` | 否 | 驳回策略：`RESTART` / `PREVIOUS` / `ORIGIN` |
| `steps` | 否 | 审批步骤列表 |

```json
{
  "name": "采购审批",
  "description": "采购申请审批流程",
  "status": 1,
  "rejectMode": "ORIGIN",
  "steps": [
    {
      "stepOrder": 1,
      "stepName": "部门审批",
      "approvers": [
        { "userId": 1 },
        { "userId": 3 }
      ]
    }
  ]
}
```

**响应字段**

| 字段 | 说明 |
|------|------|
| `data` | 审批流 ID |

```json
{
  "code": 200,
  "msg": "success",
  "data": 3
}
```

## 14) 部门列表

### `GET /api/dept/list`

**响应字段**

| 字段 | 说明 |
|------|------|
| `id` | 部门 ID |
| `name` | 部门名称 |
| `parentId` | 上级部门 ID |
| `createTime` | 创建时间 |

```json
{
  "code": 200,
  "msg": "success",
  "data": [
    {
      "id": 1,
      "name": "技术部",
      "parentId": 0,
      "createTime": "2026-07-06 09:00:00"
    },
    {
      "id": 3,
      "name": "管理层",
      "parentId": 0,
      "createTime": "2026-07-06 09:00:00"
    }
  ]
}
```

## 15) 用户列表

### `GET /api/dept/users`

**响应字段**

| 字段 | 说明 |
|------|------|
| `id` | 用户 ID |
| `username` | 用户名 |
| `role` | 角色 |
| `deptId` | 部门 ID |
| `deptName` | 部门名称 |
| `createTime` | 创建时间 |
| `updateTime` | 更新时间 |

```json
{
  "code": 200,
  "msg": "success",
  "data": [
    {
      "id": 1,
      "username": "admin",
      "password": null,
      "role": "APPROVER",
      "deptId": 3,
      "deptName": "管理层",
      "createTime": "2026-07-06 09:00:00",
      "updateTime": "2026-07-06 09:00:00"
    }
  ]
}
```


## 16) 用户分页

### `GET /api/users/page`

**Query 参数**

| 参数 | 必填 | 说明 |
|------|------|------|
| `page` | 否 | 页码，默认 `1` |
| `size` | 否 | 每页数量，默认 `10` |
| `username` | 否 | 用户名关键字 |
| `role` | 否 | `ADMIN` / `APPROVER` / `USER` |
| `deptId` | 否 | 部门 ID |
| `status` | 否 | `1` 启用，`0` 禁用 |

**响应字段**

| 字段 | 说明 |
|------|------|
| `records` | 用户列表 |
| `total` | 总条数 |
| `size` | 每页数量 |
| `current` | 当前页码 |
| `pages` | 总页数 |

```json
{
  "code": 200,
  "msg": "success",
  "data": {
    "records": [
      {
        "id": 1,
        "username": "admin",
        "password": null,
        "role": "ADMIN",
        "deptId": 3,
        "status": 1,
        "deptName": "管理层",
        "createTime": "2026-07-06 09:00:00",
        "updateTime": "2026-07-06 09:00:00"
      }
    ],
    "total": 1,
    "size": 10,
    "current": 1,
    "pages": 1
  }
}
```

## 17) 启用用户列表

### `GET /api/users/list`

**响应字段**

| 字段 | 说明 |
|------|------|
| `id` | 用户 ID |
| `username` | 用户名 |
| `role` | 角色 |
| `deptId` | 部门 ID |
| `status` | 状态 |
| `deptName` | 部门名称 |

```json
{
  "code": 200,
  "msg": "success",
  "data": [
    {
      "id": 2,
      "username": "user1",
      "password": null,
      "role": "USER",
      "deptId": 1,
      "status": 1,
      "deptName": "技术部",
      "createTime": "2026-07-06 09:00:00",
      "updateTime": "2026-07-06 09:00:00"
    }
  ]
}
```

## 18) 创建用户

### `POST /api/users`

**请求体**

| 参数 | 必填 | 说明 |
|------|------|------|
| `username` | 是 | 用户名 |
| `password` | 是 | 初始密码 |
| `role` | 是 | `ADMIN` / `APPROVER` / `USER` |
| `deptId` | 否 | 部门 ID |
| `status` | 是 | `1` 启用，`0` 禁用 |

```json
{
  "username": "manager1",
  "password": "123456",
  "role": "APPROVER",
  "deptId": 1,
  "status": 1
}
```

**响应字段**

| 字段 | 说明 |
|------|------|
| `data` | 用户 ID |

```json
{
  "code": 200,
  "msg": "success",
  "data": 4
}
```

## 19) 更新用户

### `PUT /api/users/{id}`

**Path 参数**

| 参数 | 必填 | 说明 |
|------|------|------|
| `id` | 是 | 用户 ID |

**请求体**

| 参数 | 必填 | 说明 |
|------|------|------|
| `role` | 是 | `ADMIN` / `APPROVER` / `USER` |
| `deptId` | 否 | 部门 ID |
| `status` | 否 | `1` 启用，`0` 禁用 |

```json
{
  "role": "APPROVER",
  "deptId": 1,
  "status": 1
}
```

**响应字段**

| 字段 | 说明 |
|------|------|
| `data` | 固定为 `null` |

```json
{
  "code": 200,
  "msg": "success",
  "data": null
}
```

## 20) 更新用户状态

### `PUT /api/users/{id}/status`

**Path 参数**

| 参数 | 必填 | 说明 |
|------|------|------|
| `id` | 是 | 用户 ID |

**请求体**

| 参数 | 必填 | 说明 |
|------|------|------|
| `status` | 是 | `1` 启用，`0` 禁用 |

```json
{
  "status": 0
}
```

**响应字段**

| 字段 | 说明 |
|------|------|
| `data` | 固定为 `null` |

```json
{
  "code": 200,
  "msg": "success",
  "data": null
}
```

## 21) 重置用户密码

### `PUT /api/users/{id}/password`

**Path 参数**

| 参数 | 必填 | 说明 |
|------|------|------|
| `id` | 是 | 用户 ID |

**请求体**

| 参数 | 必填 | 说明 |
|------|------|------|
| `password` | 是 | 新密码 |

```json
{
  "password": "123456"
}
```

**响应字段**

| 字段 | 说明 |
|------|------|
| `data` | 固定为 `null` |

```json
{
  "code": 200,
  "msg": "success",
  "data": null
}
```

## 22) 看板统计

### `GET /api/dashboard/stats`

**响应字段**

| 字段 | 说明 |
|------|------|
| `total` | 工单总数 |
| `pending` | 待审批数量 |
| `reviewing` | 审批中数量 |
| `approved` | 已通过数量 |
| `rejected` | 已驳回数量 |
| `closed` | 已关闭数量 |
| `returned` | 退回修改数量 |
| `todayNew` | 今日新增数量 |
| `todayApproved` | 今日通过数量 |

```json
{
  "code": 200,
  "msg": "success",
  "data": {
    "total": 28,
    "pending": 3,
    "reviewing": 6,
    "approved": 15,
    "rejected": 1,
    "closed": 1,
    "returned": 2,
    "todayNew": 4,
    "todayApproved": 3
  }
}
```

## 23) 看板趋势

### `GET /api/dashboard/trend`

**响应字段**

| 字段 | 说明 |
|------|------|
| `dates` | 日期列表 |
| `newCounts` | 新增工单数量列表 |
| `approveCounts` | 通过工单数量列表 |

```json
{
  "code": 200,
  "msg": "success",
  "data": {
    "dates": ["06-30", "07-01", "07-02", "07-03", "07-04", "07-05", "07-06"],
    "newCounts": [2, 3, 4, 1, 5, 4, 4],
    "approveCounts": [1, 2, 3, 2, 4, 3, 3]
  }
}
```
