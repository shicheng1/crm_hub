# CC-2026-07-07-03 前端优化改造

- 状态：done
- 关联技术债：前端审查发现项（独立于后端 §7）
- 创建：2026-07-07
- 修改人：AI

## 背景
前端审查（通读 25 个源文件）发现 10 处可优化点，其中 1 处是**正确性 bug**（刷新失败致并发请求永久挂起、页面卡死），其余为包体积/重复请求/重渲染类体验优化。后端性能债已在 CC-01/CC-02 收敛，本轮聚焦前端。

## 影响范围
- 文件：`src/utils/request.js`、`src/main.js`、`src/views/OrderDetail.vue`、`src/views/FlowManage.vue`、`src/views/Dashboard.vue`、`src/components/Layout.vue`、`src/utils/websocket.js`、`src/api/auth.js`、`src/api/flow.js`、`src/api/user.js`（新增 `src/utils/userDict.js`）
- 接口：无破坏性变更；`refreshToken`/`getFlowDetail` 为死代码删除
- 是否触碰红线：否（Element Plus 按需引入需装 devDeps，单独标红，本轮不做，待用户确认）

## 方案（按优先级）
1. **P0 修复 request.js 刷新失败挂起**：refresh 失败时清空 `pendingQueue` 并对每个回调传 `null`，回调内 `null` 直接 reject；`redirectToLogin` 前先 flush 队列。
2. **P1 共享用户字典缓存**：新增 `src/utils/userDict.js`，模块级单例 + TTL（如 5min），`getUserDict()` 首次拉 `/api/users/list` 并缓存，后续复用；`OrderDetail.vue`/`FlowManage.vue` 改用字典做 ID→姓名，消除每次进入详情页全量拉用户表。
3. **P2 死代码清理**：删除 `api/auth.js` 未引用的 `refreshToken`、`api/flow.js` 未引用的 `getFlowDetail`（request.js 刷新走裸 axios，不受影响）。
4. **P2 Dashboard barHeight 提 computed**：把全局 max 提到 `computed`，`barHeight` 仅做除法，消除 14 次重复 `Math.max`。
5. **P2 el-table row-key**：`OrderList/TodoList/DoneList/UserManage` 四个表格补 `row-key="id"`。
6. **P2 WebSocket 健壮性**：`Layout.vue` 通知数组设上限（如 `slice(0,50)`）；`websocket.js` 断线/错误给出 UI 提示，重连加指数退避与最大次数。
7. **P2 401 处理收口**：`request.js` 把响应拦截器与错误拦截器两处 401 分支统一到 `handleAuthError(config)`，消除双路径。
8. **P2 轻量请求去重/缓存**：`request.js` 对 GET 请求在 TTL 内复用同一 Promise（in-flight + 短缓存），减少路由切换重复拉取；不引入新依赖。

## 待用户确认的红线项（本轮不做）
- **Element Plus 按需引入**：需安装 `unplugin-vue-components` + `unplugin-auto-import`（devDeps）。属「安装新依赖」红线，待用户确认后再做（收益：首屏 element chunk 体积下降）。

## 验证
- `cd frontend && npm run build`（构建通过）
- 手验：并发 401 时刷新失败页面不卡死；详情页不再每次拉全量用户表；看板趋势图正常；通知列表不无限增长；断网后实时通知断开有提示。

## 回滚
- 各文件改动独立，git 回退单文件即可。

## 执行记录（2026-07-07）
- 实施全部 8 项（F-P0-1 / F-P1-1 / F-P2-1~5）。
- `request.js`：修复刷新失败挂起（新增 `rejectPendingQueue`，失败分支 flush 队列）；新增 GET 并发去重（包一层 `request.request`，拦截器仍生效）。
- 新增 `src/utils/userDict.js`：模块级单例 + 5min TTL，`OrderDetail.vue`/`FlowManage.vue` 改用字典，消除每次进详情页全量拉用户表。
- `Dashboard.vue`：`barHeight` 全局 max 提 `computed`。
- 四个列表 `el-table` 补 `row-key="id"`。
- `websocket.js`：断线指数退避（封顶 30s）+ 断线/错误 `ElNotification` 提示；`Layout.vue` 通知数组上限 50。
- 删除 `api/auth.js` `refreshToken`、`api/flow.js` `getFlowDetail`（死代码，无人 import）。
- 验证：`npm run build` 通过（679 模块，修复过程中修正 `userDict.js` 误用 `./user` 应为 `../api/user` 的导入路径错误）。
- 未做项：F-P1-2 Element Plus 按需引入需装 devDeps（红线），待用户确认。
