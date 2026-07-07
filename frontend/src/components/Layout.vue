<template>
  <el-container class="app-shell">
    <!-- 侧边栏 -->
    <el-aside width="216px" class="app-sidebar">
      <div class="app-logo">
        <div class="app-logo__mark">审</div>
        <div>
          <div class="app-logo__title">工单审批</div>
          <div class="app-logo__sub">Order Flow</div>
        </div>
      </div>
      <el-menu :default-active="$route.path" router class="app-menu" background-color="#182235" text-color="#cbd5e1" active-text-color="#ffffff">
        <el-menu-item index="/dashboard">
          <el-icon><DataAnalysis /></el-icon>
          <span>数据看板</span>
        </el-menu-item>
        <el-menu-item index="/orders">
          <el-icon><Document /></el-icon>
          <span>工单列表</span>
        </el-menu-item>
        <el-menu-item index="/orders/create">
          <el-icon><Plus /></el-icon>
          <span>创建工单</span>
        </el-menu-item>
        <el-menu-item index="/todo">
          <el-icon><Bell /></el-icon>
          <span>待我审批</span>
        </el-menu-item>
        <el-menu-item index="/done">
          <el-icon><CircleCheck /></el-icon>
          <span>我已审批</span>
        </el-menu-item>
        <el-menu-item index="/flows">
          <el-icon><Share /></el-icon>
          <span>审批流管理</span>
        </el-menu-item>
        <el-menu-item index="/users">
          <el-icon><User /></el-icon>
          <span>用户管理</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <!-- 顶部栏 -->
      <el-header class="app-header">
        <div class="app-header__title">{{ $route.meta.title || '工单审批系统' }}</div>
        <div class="app-header__actions">
          <!-- 通知铃铛 -->
          <el-badge :value="unreadCount" :hidden="unreadCount === 0" :max="99">
            <el-icon class="notification-icon" @click="showNotificationPanel = !showNotificationPanel">
              <Bell />
            </el-icon>
          </el-badge>
          <el-tag effect="plain">{{ { ADMIN: '管理员', APPROVER: '审批人', USER: '普通用户' }[user?.role] || '普通用户' }}</el-tag>
          <span class="user-name">{{ user?.username }}</span>
          <el-button type="info" size="small" @click="handleLogout">退出</el-button>
        </div>
      </el-header>

      <!-- 通知面板 -->
      <el-drawer v-model="showNotificationPanel" title="通知中心" :size="360" direction="rtl">
        <div v-if="notifications.length === 0" style="text-align: center; color: #999; padding: 40px 0;">暂无通知</div>
        <div v-for="(item, index) in notifications" :key="index"
             style="padding: 12px; border-bottom: 1px solid #eee; cursor: pointer;"
             @click="goToOrder(item.orderId)">
          <div style="display: flex; justify-content: space-between; align-items: center;">
            <el-tag :type="item.result === 'APPROVED' ? 'success' : 'danger'" size="small">
              {{ item.result === 'APPROVED' ? '已通过' : '已驳回' }}
            </el-tag>
            <span style="color: #999; font-size: 12px;">{{ formatTime(item.approveTime) }}</span>
          </div>
          <div style="margin-top: 6px; font-size: 14px;">
            工单「{{ item.orderTitle }}」被 <b>{{ item.approverName }}</b>
            {{ item.result === 'APPROVED' ? '通过' : '驳回' }}
          </div>
          <div v-if="item.remark" style="margin-top: 4px; color: #666; font-size: 13px;">备注：{{ item.remark }}</div>
        </div>
      </el-drawer>

      <!-- 主内容 -->
      <el-main class="app-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { getUser, removeToken } from '../utils/auth'
import { logout } from '../api/auth'
import { connectWebSocket, disconnectWebSocket } from '../utils/websocket'
import { bumpOrderChange } from '../utils/orderBus'

const router = useRouter()
const user = getUser()
const unreadCount = ref(0)
const notifications = ref([])
const showNotificationPanel = ref(false)

const handleLogout = async () => {
  try { await logout() } catch (e) {}
  removeToken()
  disconnectWebSocket()
  router.push('/login')
}

const goToOrder = (orderId) => {
  showNotificationPanel.value = false
  router.push(`/orders/${orderId}`)
}

const formatTime = (time) => time ? time.replace('T', ' ').substring(0, 19) : ''

onMounted(() => connectWebSocket((payload) => {
  notifications.value.unshift(payload)
  // 通知列表设上限，避免长连接下无限增长占用内存
  if (notifications.value.length > 50) {
    notifications.value = notifications.value.slice(0, 50)
  }
  unreadCount.value++
  // 业务流转（他人审批/创建等）广播到变更总线，当前打开的列表/看板自动刷新
  bumpOrderChange()
}))
onUnmounted(() => disconnectWebSocket())
</script>

<style scoped>
.app-shell {
  height: 100vh;
  background: var(--app-bg);
}

.app-sidebar {
  background: var(--app-sidebar);
  box-shadow: 8px 0 24px rgba(15, 23, 42, 0.12);
}

.app-logo {
  height: 64px;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 18px;
  color: #fff;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.app-logo__mark {
  width: 34px;
  height: 34px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  background: var(--app-primary);
  font-weight: 700;
}

.app-logo__title {
  font-size: 16px;
  font-weight: 700;
  line-height: 1.2;
}

.app-logo__sub {
  margin-top: 3px;
  color: #94a3b8;
  font-size: 12px;
}

.app-menu {
  border-right: none;
  padding: 10px 8px;
}

.app-menu :deep(.el-menu-item) {
  height: 44px;
  border-radius: 6px;
  margin-bottom: 4px;
}

.app-menu :deep(.el-menu-item.is-active) {
  background: var(--app-sidebar-active);
}

.app-header {
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  border-bottom: 1px solid var(--app-border);
  padding: 0 22px;
}

.app-header__title {
  font-size: 16px;
  font-weight: 650;
  color: var(--app-text);
}

.app-header__actions {
  display: flex;
  align-items: center;
  gap: 14px;
}

.notification-icon {
  font-size: 20px;
  cursor: pointer;
  color: var(--app-subtle);
}

.user-name {
  font-weight: 650;
}

.app-main {
  padding: 22px;
  background: var(--app-bg);
  overflow: auto;
}
</style>
