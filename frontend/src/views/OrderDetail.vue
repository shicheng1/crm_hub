<template>
  <div v-if="order">
    <div style="display: flex; justify-content: space-between; margin-bottom: 16px;">
      <h3>工单详情 #{{ order.id }}</h3>
      <el-button @click="$router.back()">返回</el-button>
    </div>

    <!-- 基本信息 -->
    <el-card style="margin-bottom: 16px;">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="标题" :span="2">{{ order.title }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusType(order.status)">{{ statusText(order.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="创建人">{{ order.creatorName || order.creatorId }}</el-descriptions-item>
        <el-descriptions-item label="审批流程">{{ order.flowName || '未选择' }}</el-descriptions-item>
        <el-descriptions-item label="驳回策略">
          {{ { RESTART: '回到第一步', PREVIOUS: '退回上一步', ORIGIN: '退回发起人' }[order.rejectMode] || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="创建时间" :span="2">{{ order.createTime }}</el-descriptions-item>
        <el-descriptions-item label="内容" :span="2">
          <div style="white-space: pre-wrap;">{{ order.content || '无' }}</div>
        </el-descriptions-item>
      </el-descriptions>
    </el-card>

    <!-- 审批流进度 -->
    <el-card v-if="order.flowSteps && order.flowSteps.length" style="margin-bottom: 16px;">
      <template #header>审批流程进度</template>
      <el-steps :active="currentStepIndex" finish-status="success">
        <el-step v-for="step in order.flowSteps" :key="step.id" :title="step.stepName">
          <template #description>
            <div>{{ getStepStatusText(step) }}</div>
            <div style="color: #999; font-size: 12px; margin-top: 4px;">
              审批人：{{ getApproversText(step) }}
            </div>
            <!-- 该步骤已审批的记录 -->
            <div v-for="rec in getStepRecords(step)" :key="rec.id" style="font-size: 12px; margin-top: 2px;">
              <el-tag :type="rec.result === 'APPROVED' ? 'success' : 'danger'" size="small">
                {{ rec.result === 'APPROVED' ? '通过' : '驳回' }}
              </el-tag>
              {{ getApproverName(rec.approverId) }}
              <span v-if="rec.remark">（{{ rec.remark }}）</span>
            </div>
          </template>
        </el-step>
      </el-steps>
    </el-card>

    <!-- 操作区 -->
    <el-card style="margin-bottom: 16px;">
      <template #header>操作</template>

      <!-- 审批人审批 -->
      <div v-if="canApprove">
        <el-input v-model="remark" type="textarea" :rows="3" placeholder="审批备注（可选）" style="margin-bottom: 12px;" />
        <el-space>
          <el-button type="success" :loading="approving" @click="handleApprove(true)">通过</el-button>
          <el-button type="danger" :loading="approving" @click="handleApprove(false)">驳回</el-button>
        </el-space>
      </div>

      <!-- 发起人重新提交 -->
      <div v-if="canResubmit">
        <el-alert title="该工单已被退回，请修改后重新提交" type="warning" :closable="false" style="margin-bottom: 12px;" />
        <el-button type="primary" :loading="resubmitting" @click="handleResubmit">重新提交</el-button>
      </div>

      <el-empty v-if="!canApprove && !canResubmit" description="当前无可用操作" :image-size="60" />
    </el-card>

    <!-- 操作日志 -->
    <el-card>
      <template #header>操作日志</template>
      <el-timeline>
        <el-timeline-item v-for="item in logs" :key="item.id"
                          :timestamp="item.operateTime?.replace('T', ' ')" placement="top"
                          :type="logType(item.operation)">
          <div><b>{{ item.operatorName }}</b> {{ item.detail }}</div>
        </el-timeline-item>
      </el-timeline>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getOrderDetail, approveOrder, resubmitOrder, getOrderLogs } from '../api/order'
import { getUserDict } from '../utils/userDict'
import { getUser } from '../utils/auth'

const route = useRoute()
const router = useRouter()
const order = ref(null)
const logs = ref([])
const remark = ref('')
const approving = ref(false)
const resubmitting = ref(false)
const user = getUser()
const userDict = ref(new Map())
const nameOf = (id) => {
  const u = userDict.value.get(id)
  return u ? u.username : ('ID:' + id)
}

const statusText = (s) => ({ 0:'待审批', 1:'审批中', 2:'已通过', 3:'已驳回', 4:'已关闭', 5:'退回修改' }[s] || '未知')
const statusType = (s) => ({ 0:'warning', 1:'', 2:'success', 3:'danger', 4:'info', 5:'warning' }[s] || 'info')
const logType = (op) => ({ CREATE:'primary', APPROVE:'success', REJECT:'danger', CLOSE:'info', RESUBMIT:'warning' }[op] || 'primary')

const currentStepIndex = computed(() => {
  if (!order.value?.flowSteps || !order.value?.currentStep) return 0
  return order.value.flowSteps.findIndex(s => s.stepOrder === order.value.currentStep)
})

// 当前用户是否可以审批
const canApprove = computed(() => {
  if (!order.value || !['ADMIN', 'APPROVER'].includes(user?.role)) return false
  if (order.value.status !== 0 && order.value.status !== 1) return false
  const step = order.value.flowSteps?.find(s => s.stepOrder === order.value.currentStep)
  if (!step) return false
  return step.approvers?.some(a => a.userId === user?.userId) || false
})

// 创建人是否可以重新提交
const canResubmit = computed(() => {
  if (!order.value) return false
  return order.value.status === 5 && order.value.creatorId === user?.userId
})

const getStepStatusText = (step) => {
  if (!order.value?.currentStep) return '待处理'
  if (step.stepOrder < order.value.currentStep) return '已通过 ✓'
  if (step.stepOrder === order.value.currentStep) return '当前步骤 ▶'
  return '待处理'
}

const getApproversText = (step) => {
  if (!step.approvers || step.approvers.length === 0) return '未配置'
  return step.approvers.map(a => nameOf(a.userId)).join('、')
}

const getStepRecords = (step) => {
  return (order.value?.records || []).filter(r => r.stepId === step.id)
}

const getApproverName = (approverId) => {
  return nameOf(approverId)
}

const loadOrder = async () => {
  const res = await getOrderDetail(route.params.id)
  order.value = res.data
  const logRes = await getOrderLogs(route.params.id)
  logs.value = logRes.data
}

onMounted(async () => {
  // 用户字典用于显示审批人姓名（带 TTL 缓存，避免每次进入详情页全量拉用户表）
  try {
    userDict.value = await getUserDict()
  } catch (e) {}
  await loadOrder()
})

const handleApprove = async (approved) => {
  approving.value = true
  try {
    await approveOrder({ orderId: order.value.id, approved, remark: remark.value })
    ElMessage.success(approved ? '审批通过' : '已驳回')
    remark.value = ''
    // 业务流转完成：跳转「待我审批」继续处理后续工单，目标列表挂载即拉取最新状态
    router.push('/todo')
  } catch (e) {} finally { approving.value = false }
}

const handleResubmit = async () => {
  resubmitting.value = true
  try {
    await resubmitOrder(order.value.id)
    ElMessage.success('重新提交成功')
    // 重提后工单重新进入审批流：跳转「工单列表」查看流转状态
    router.push('/orders')
  } catch (e) {} finally { resubmitting.value = false }
}
</script>
