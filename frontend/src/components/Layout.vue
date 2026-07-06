<template>
  <el-container style="height: 100vh">
    <!-- 侧边栏 -->
    <el-aside width="200px" style="background: #304156;">
      <div style="height: 50px; display: flex; align-items: center; justify-content: center; color: #fff; font-size: 16px; font-weight: bold;">
        工单审批系统
      </div>
      <el-menu :default-active="$route.path" router background-color="#304156" text-color="#bfcbd9" active-text-color="#409eff">
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
      </el-menu>
    </el-aside>

    <el-container>
      <!-- 顶部栏 -->
      <el-header style="display: flex; align-items: center; justify-content: flex-end; background: #fff; border-bottom: 1px solid #eee;">
        <div style="display: flex; align-items: center; gap: 16px;">
          <!-- 通知铃铛 -->
          <el-badge :value="unreadCount" :hidden="unreadCount === 0" :max="99">
            <el-icon style="font-size: 20px; cursor: pointer" @click="showNotificationPanel = !showNotificationPanel">
              <Bell />
            </el-icon>
          </el-badge>
          <el-tag>{{ user?.role === 'APPROVER' ? '审批人' : '普通用户' }}</el-tag>
          <span style="font-weight: bold;">{{ user?.username }}</span>
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
      <el-main style="padding: 20px; background: #f5f5f5;">
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
  unreadCount.value++
}))
onUnmounted(() => disconnectWebSocket())
</script>
