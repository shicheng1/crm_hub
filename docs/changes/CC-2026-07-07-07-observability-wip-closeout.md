# CC-2026-07-07-07 · 收口 observability 分支 WIP

- 作者：AI（shicheng1 既有 WIP 的收口）
- 分支：`feature/p4-observability-hy`
- 关联 WIP 提交：[`6dcaf70`](../../.git) `feat: 携带可观测性分支半截工作（observability，WIP 待续）`
- 状态：`done`（前端已构建验证；后端待用户 Java8 环境编译验证）

## 1. 背景

用户推送后要求"继续"，当前分支 `feature/p4-observability-hy` 上有个未经本机验证的 WIP 提交 `6dcaf70`。
按 AGENTS.md §0 先读、再审查、再收口。审查发现：**该 WIP 实际由多个不相关杂项组成，并不含任何可观测性组件**（pom 仅新增 `flyway-mysql`，无 actuator/prometheus/micrometer 依赖）。

本计划对 WIP 逐项审查、修复唯一真 bug、验证前端构建、补齐文档。

## 2. WIP 逐项审查结论

| # | 改动 | 文件 | 结论 | 风险 |
|---|------|------|------|------|
| 1 | 新增 `flyway-mysql` 依赖 | `backend/pom.xml` | ✅ 合理。Flyway 8.5.13（SB 2.7 托管）对 MySQL 小版本封顶 8.0.31，连 8.0.32+ 启动抛 `Unsupported Database` | 待 Java8 编译验证 |
| 2 | MySQL 镜像钉 `8.0.31` | `docker-compose.yml` | ✅ 配合 #1；注释已写清原因（升 Flyway 9 与 SB2.7 不兼容，故钉 MySQL 而非升 Flyway） | 需重建 `mysql_data` 卷 |
| 3 | dev proxy `localhost`→`127.0.0.1` | `frontend/vite.config.js` | ✅ 修复 `localhost` 解析 IPv6(`::1`) 导致 proxy 连不上后端 IPv4 监听 | 无，前端 build 通过 |
| 4 | 引入全局样式 `app.css` | `frontend/src/main.js` + `frontend/src/styles/app.css` | ✅ 设计系统基础（CSS 变量 + Element Plus 圆角/阴影覆盖 + 页面布局类）。现有页面尚未采用这些类（dead styles），待逐步采用 | 无破坏性，build 通过 |
| 5 | 登录按钮加 `@click="handleLogin"` | `frontend/src/views/Login.vue` | ❌ **双触发 bug**：`el-form` 已有 `@submit.prevent="handleLogin"` 且按钮 `native-type="submit"`，再加 `@click` 会使点击触发两次 `handleLogin`（先 click 后 submit），可能重复发登录请求 | **已修复**：去掉 `@click`，保留表单 submit |
| 6 | `/api/users/list` 移到 `UserReferenceController` | `UserController.java`(删) + 新增 `UserReferenceController.java` | ✅ 权限分离合理：`UserController` 类级 `@RequireRole("ADMIN")`，只读参考列表（审批人姓名等）不应要求 ADMIN，拆分到无权限注解的 `UserReferenceController`。两 controller 基类路径同为 `/api/users`，方法级路径不冲突（Spring 允许） | 待 Java8 编译验证 |
| 7 | 测试数据种子脚本 | `scripts/seed_test_data.sql` | ✅ 幂等（`INSERT...SELECT...WHERE NOT EXISTS`）。表/列已核对与 Flyway V1~V5 完全一致：`sys_user`(username,password,role,dept_id,status)、`approval_flow`(name,description,status,reject_mode)、`approval_flow_step`(flow_id,step_order,step_name,approve_mode)、`approval_step_approver`(step_id,user_id) | 手动脚本，需文档说明执行方式 |

## 3. 修复

- `frontend/src/views/Login.vue`：删除登录按钮冗余的 `@click="handleLogin"`，仅保留 `el-form @submit.prevent` + 按钮 `native-type="submit"`，消除重复触发。

## 4. 验证

- 前端：在 `frontend/` 执行 `npm run build`，通过（679 模块，element chunk 1MB 为已知 F-P1-2 待办，与本次无关）。
- 后端：`flyway-mysql` 依赖新增 + `UserReferenceController` 新增为纯结构改动，但本地 JDK/Maven 损坏，按项目惯例由用户在 Java8 环境 `mvn test` 验证编译。

## 5. 文档收口

- `README.md`：
  - 「项目状态总览 / 进行中变更」表补齐 CC-05/06/07（此前只到 CC-04）。
  - 「环境要求」MySQL 注明钉定 `8.0.31` 及 Flyway 兼容性原因。
  - 新增「加载测试数据（可选）」小节，说明 `mysql order_approval < scripts/seed_test_data.sql`（幂等，密码统一 123456）。
- `AGENTS.md`：§3.3 索引追加 CC-07；§7 记录本次杂项收口（权限分离、seed 脚本）。

## 6. 回滚

- 撤销本次 Login.vue 修复：`git revert` 或恢复 `@click`。
- 其余 WIP 改动回滚：`git revert 6dcaf70`（整段 WIP 连同本次修复一并回退）。
