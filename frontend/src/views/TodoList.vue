<template>
  <div>
    <PageHeader title="待我审批" subtitle="需要你处理的工作流审批任务">
      <el-button :icon="Refresh" @click="load" :loading="loading">刷新</el-button>
    </PageHeader>

    <el-card>
      <el-table :data="orders" stripe v-loading="loading" row-key="id">
        <el-table-column label="ID" width="92">
          <template #default="{ row }"><span class="id-cell">#{{ row.id }}</span></template>
        </el-table-column>
        <el-table-column prop="title" label="标题" min-width="200" />
        <el-table-column prop="flowName" label="审批流程" min-width="140" show-overflow-tooltip />
        <el-table-column prop="creatorName" label="创建人" width="120">
          <template #default="{ row }">{{ row.creatorName || ('用户#' + row.creatorId) }}</template>
        </el-table-column>
        <el-table-column prop="currentStep" label="当前步骤" width="100" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="180" />
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button type="primary" link @click="$router.push(`/orders/${row.id}`)">去审批</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty :image-size="60" description="暂无待办审批" />
        </template>
      </el-table>
      <el-pagination class="pager"
          v-model:current-page="page" :total="total" :page-size="10"
          layout="total, prev, pager, next" @current-change="load" />
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted, watch } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import PageHeader from '../components/PageHeader'
import { getTodoList } from '../api/order'
import { orderBus } from '../utils/orderBus'

const orders = ref([])
const page = ref(1)
const total = ref(0)
const loading = ref(false)

const statusText = (s) => ({ 0:'待审批', 1:'审批中', 2:'已通过', 3:'已驳回', 4:'已关闭', 5:'退回修改' }[s] || '未知')
const statusType = (s) => ({ 0:'warning', 1:'', 2:'success', 3:'danger', 4:'info', 5:'warning' }[s] || 'info')

const load = async () => {
  loading.value = true
  try {
    const res = await getTodoList({ page: page.value, size: 10 })
    orders.value = res.data.records
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

onMounted(load)
// 业务流转（他人审批/创建等）经 WebSocket 推送到总线后，自动重新拉取待办
watch(() => orderBus.revision, load)
</script>
