# CC-2026-07-07-05 前端业务流转后视图刷新与跳转

- 状态：done
- 关联技术债：§7 前端技术债 F-P3（业务流转后视图不刷新 / 不跳转）
- 创建：2026-07-07
- 修改人：AI

## 背景
用户反馈：前端有些地方业务流转后「没有刷新」或「没有跳转到对应的页面」。排查代码确认两处真实缺陷：
1. WebSocket 审批通知到达后，`Layout` 仅更新铃铛红点与通知抽屉，当前打开的 `TodoList / DoneList / OrderList / Dashboard` 不联动刷新，必须手动点刷新（且 `DoneList` 连刷新按钮都没有）。
2. `OrderDetail` 的审批/驳回/重提操作完成后仅原地 `loadOrder()`，用户被留在详情页，未回到「待我审批 / 工单列表」继续流转。

## 影响范围
- 文件：`frontend/src/utils/orderBus.js`（新增）、`Layout.vue`、`TodoList.vue`、`DoneList.vue`、`OrderList.vue`、`Dashboard.vue`、`OrderDetail.vue`
- 接口：无新增/变更，仅前端视图联动
- 是否触碰红线：否

## 方案
1. 新增 `orderBus.js`：导出 `reactive` 的 `orderBus.revision` 与 `bumpOrderChange()`，作为工单状态变更信号总线。
2. `Layout.vue`：WebSocket `onMessage` 回调中增加 `bumpOrderChange()`，把"他人审批/创建"等远程流转广播给所有订阅视图。
3. `TodoList / DoneList / OrderList / Dashboard`：`watch(() => orderBus.revision, load)` 自动重拉；`DoneList` 补「刷新」按钮与 `Refresh` 图标，与 `TodoList` 对齐。
4. `OrderDetail`：审批/驳回成功后 `router.push('/todo')`（回到待办继续处理，工单已离开队列即直观反馈）；重提成功后 `router.push('/orders')`（工单重新进入审批流，去列表查看流转）。本地操作靠目标列表挂载即重拉，无需再 `bump`，避免重复加载。
5. 不引入 `keep-alive`：当前 `<router-view>` 无缓存，手动返回本就会重挂载刷新，引入缓存反而会增加状态管理复杂度，故采用总线 + 跳转的最小改动方案。

## 验证
- 命令：`cd frontend && npm run build`
- 预期：构建通过；审批通知到达时当前列表/看板自动刷新；审批后跳回待办、重提后跳回工单列表。

## 回滚
- 删除 `orderBus.js`，撤销各视图对 `orderBus` 的引用与 `OrderDetail` 的 `router.push` 即可回退。
