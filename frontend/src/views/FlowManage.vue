<template>
  <div>
    <h3>审批流管理</h3>
    <el-card v-for="flow in flows" :key="flow.id" style="margin-bottom: 16px;">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center;">
          <div>
            <span style="font-weight: bold;">{{ flow.name }}</span>
            <el-tag style="margin-left: 8px;" size="small">
              驳回策略：{{ { RESTART: '回到第一步', PREVIOUS: '退回上一步', ORIGIN: '退回发起人' }[flow.rejectMode] || '未配置' }}
            </el-tag>
          </div>
          <el-tag :type="flow.status === 1 ? 'success' : 'info'">{{ flow.status === 1 ? '启用' : '禁用' }}</el-tag>
        </div>
      </template>
      <div style="color: #666; margin-bottom: 12px;">{{ flow.description }}</div>
      <el-steps :active="-1" finish-status="success" simple>
        <el-step v-for="step in flow.steps" :key="step.id" :title="step.stepName">
          <template #description>
            <div>审批人：{{ formatApprovers(step) }}</div>
          </template>
        </el-step>
      </el-steps>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getFlowList } from '../api/flow'

const flows = ref([])

const formatApprovers = (step) => {
  if (!step.approvers || step.approvers.length === 0) return '未配置'
  return step.approvers.map(a => '用户' + a.userId).join(' 或 ')
}

onMounted(async () => {
  const res = await getFlowList()
  flows.value = res.data
})
</script>
