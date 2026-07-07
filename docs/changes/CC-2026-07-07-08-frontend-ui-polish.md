# CC-2026-07-07-08 前端整体展示 / 交互效果 / ID→文字 优化

- 状态：done
- 关联技术债：§7 前端技术债（F-P1-1 已有 userDict；本次属展示层增强，新增 F-P4 记录）
- 创建：2026-07-07
- 修改人：AI

## 背景
用户要求优化前端：①整体展示；②交互效果；③「有些地方显示 ID 而不是文字」。
审查结论：后端已回填 `creatorName` / `flowName`（OrderServiceImpl 283/289/333/339），`deptName` 也由 `UserServiceImpl` 回填，所以列表/详情**已能拿到名字**，问题集中在「兜底显示裸数字」与「未落地既有设计系统」。
特别发现：`frontend/src/styles/app.css` 已定义完整设计 token（`.page-header`/`.page-title`/`.toolbar-card`/`.content-card`/`.stat-grid`/`.stat-card` 等），但所有视图仍用内联 `<h3>` + 裸 `<el-card>`，**设计系统完全未被采用**。本次优先复用既有 token，不新增样式变量，风险最低。

## 影响范围
- 文件（预计）：全部 view（OrderList/TodoList/DoneList/OrderDetail/OrderCreate/FlowManage/UserManage/Dashboard/Login/NotFound）+ `App.vue`（路由过渡）+ `app.css`（补过渡 keyframes）+ `userDict.js`（兜底文案）+ 可能新增 `components/PageHeader.vue`。
- 接口：无新增/变更（flowName/creatorName 后端已给）。
- 是否触碰红线：否（纯前端，无 DB / 无依赖安装 / 无 push）。

## 方案（分类，按需确认范围）

### C1 — ID/文字映射收口（用户明确点名，最高优先级）
1. 所有 `('ID:' + id)` / `order.creatorId` / `nameOf` 兜底：改为统一走 `userDict` 解析用户名；`userDict` 未命中时显示 `用户#${id}`（带"用户"前缀，不再裸数字 / 裸 "ID:"）。
   - 涉及：`OrderList.vue:38`、`TodoList.vue:12`、`OrderDetail.vue:15,105`。
2. 列表原始 `ID` 数字列（OrderList:27 / DoneList:9 / UserManage:40）：保留但弱化为次级信息（显示为 `#1001` 形态、muted 小字、收窄宽度），不再作为主视觉列；或按用户偏好直接隐藏。（待用户拍板）
3. 列表补充「审批流程」列，用已有的 `flowName`（OrderList/TodoList/DoneList），让工单归属清晰。
4. 审批人姓名已用 `userDict`，确认无裸 id 残留。

### C2 — 落地既有设计系统（整体展示，复用 app.css，零新增变量）
5. 各 view 内联 `<h3>` + flex 标题栏 → 统一改用 `.page-header` / `.page-title` 类（或新建 `components/PageHeader.vue`：标题 + 副标题 + 右侧操作插槽）。推荐新建组件，消除各页重复结构。
6. 筛选/工具区包进 `.toolbar-card`，表格/详情包进 `.content-card`（或直接用 `.el-card` + 已有 header 样式），统一间距与圆角阴影（app.css 已配）。
7. Dashboard 统计卡改用 `.stat-grid` / `.stat-card`（app.css 已有类），统一看板视觉。
8. 统一分页器：去掉内联 `justify-content: flex-end`，改用 Element Plus `el-pagination` 的 `justify` 属性（或 flex 包一层）。

### C3 — 交互效果（过渡 + 空状态 + 404）
9. `App.vue` 的 `<router-view>` 外包 `<transition name="fade-slide">`；`app.css` 补对应 keyframes/transition 类，路由切换淡入 + 轻微上滑。
10. 各 `el-table` 补 `empty` 插槽：友好空态（图标 + 文案，如「暂无工单」「暂无用户」），替代默认空白。
11. `NotFound.vue` 美化：用设计系统风格做插画感 404 卡片 + 操作引导（返回首页）。

### C4 — 细节润色（可选，按用户兴趣）
12. 表格行 hover 已由 app.css 配色；可加操作列 hover 显隐 / 按钮统一样式。
13. Dashboard 数字 count-up（轻量，可选）。
14. 关键操作已有二次确认（toggleStatus 等），保留。

## 验证
- 命令：`cd frontend && npm run build`（必须构建通过）
- 手验：进入各页面确认 ①无裸数字 ID 显示（creator/审批人/流程均显示文字）②标题栏/卡片风格统一 ③路由切换有过渡 ④空列表显示友好空态 ⑤404 页美观。

## 回滚
- 纯前端展示层改动；`git revert` 对应提交或 checkout 相关文件即可还原，无数据/后端影响。

## 实施记录（2026-07-07）
- 新增 `frontend/src/components/PageHeader.vue`：统一标题 + 副标题 + 右侧操作插槽，替代各视图内联 `<h3>` + flex。
- `app.css` 补：`.page-actions`（标题操作区）、`.pager`（分页右对齐）、`.id-cell`（主键弱化为 `#1001` 灰色小字）、`.fade-slide`（路由切换淡入上滑过渡 keyframes）。
- `App.vue` 的 `<router-view>` 外包 `<transition name="fade-slide" mode="out-in">`。
- 全部视图（OrderList/TodoList/DoneList/OrderDetail/OrderCreate/FlowManage/UserManage）标题改用 `PageHeader`；列表 ID 列弱化为 `#{{id}}`；creator/审批人兜底由裸 `ID:xxx` 改为 `用户#xxx`；列表补「审批流程」列（用后端已有 `flowName`）；表格加 `#empty` 友好空态（`el-empty`）；分页改 `.pager` class。
- `FlowManage` 审批人兜底、`OrderDetail` `nameOf` 兜底均改为 `用户#id`。
- `UserManage` 筛选区由 `el-card` 改为 `.toolbar-card`，表头风格统一。
- `NotFound` 用设计系统风格重做 404 卡片；`Login` 加品牌头与渐变背景。
- `Dashboard` 看板数字加 count-up（easeOutCubic，600ms），数据刷新平滑过渡。
- `vite.config.js` 的 `resolve.extensions` 补 `.vue`（允许组件省略扩展名导入，标准配置）。
- 验证：`npm run build` 通过（508 模块；element chunk 1MB 为已知 F-P1-2，未动）。
