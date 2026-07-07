import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client/dist/sockjs'
import { getToken, getUser } from './auth'
import { ElNotification } from 'element-plus'

let stompClient = null
let reconnectAttempts = 0
const BASE_RECONNECT = 5000
const MAX_RECONNECT = 30000

/**
 * 连接 WebSocket（STOMP over SockJS）
 *
 * 和 CRM 项目中工作流回调的思路类似：
 * - CRM 通过 MQ 接收审批结果通知
 * - 这里通过 WebSocket 实时推送到前端
 *
 * 订阅方式：/topic/notifications/{userId}（按用户 ID 订阅个人频道）
 */
export function connectWebSocket(onMessage) {
  const user = getUser()
  if (!user) return

  stompClient = new Client({
    webSocketFactory: () => new SockJS('/ws'),
    reconnectDelay: BASE_RECONNECT,
    heartbeatIncoming: 4000,
    heartbeatOutgoing: 4000,

    onConnect: () => {
      console.log('WebSocket 已连接, userId=' + user.userId)
      reconnectAttempts = 0

      // 订阅个人通知频道
      const destination = '/topic/notifications/' + user.userId
      stompClient.subscribe(destination, (message) => {
        const payload = JSON.parse(message.body)
        console.log('收到实时通知:', payload)

        // 弹窗通知
        showNotification(payload)

        // 回调
        if (onMessage) {
          onMessage(payload)
        }
      })

      console.log('已订阅: ' + destination)
    },

    onStompError: (frame) => {
      console.error('WebSocket STOMP 错误:', frame.headers['message'])
      ElNotification({
        title: '实时通知异常',
        message: '通知连接出现问题，将自动重连',
        type: 'warning',
        duration: 4000
      })
    },

    onWebSocketClose: () => {
      reconnectAttempts++
      // 指数退避，封顶 30s，避免网络抖动时频繁重连打满
      stompClient.reconnectDelay = Math.min(MAX_RECONNECT, BASE_RECONNECT * Math.pow(2, reconnectAttempts))
      console.warn(`WebSocket 已断开，第 ${reconnectAttempts} 次重连（间隔 ${stompClient.reconnectDelay}ms）`)
      if (reconnectAttempts === 1) {
        ElNotification({
          title: '实时通知已断开',
          message: '正在尝试重新连接…',
          type: 'warning',
          duration: 4000
        })
      }
    }
  })

  stompClient.activate()
}

export function disconnectWebSocket() {
  if (stompClient) {
    stompClient.deactivate()
    stompClient = null
  }
  reconnectAttempts = 0
}

function showNotification(payload) {
  const isApproved = payload.result === 'APPROVED'
  ElNotification({
    title: isApproved ? '工单审批通过' : '工单被驳回',
    message: `您的工单「${payload.orderTitle}」已被 ${payload.approverName} ${isApproved ? '通过' : '驳回'}${payload.remark ? '，备注：' + payload.remark : ''}`,
    type: isApproved ? 'success' : 'warning',
    duration: 8000,
    position: 'top-right'
  })
}
