import { reactive } from 'vue'

/**
 * 工单业务状态变更信号总线。
 *
 * 任何导致工单状态流转的来源（WebSocket 实时审批通知、或后续本地审批/创建/重提操作）
 * 调用 bumpOrderChange() 后，订阅了 orderBus.revision 的列表页 / 看板会自动重新拉取，
 * 从根本上解决「业务流转后视图不刷新」的问题，无需引入 keep-alive 缓存。
 */
export const orderBus = reactive({ revision: 0 })

export function bumpOrderChange() {
  orderBus.revision++
}
