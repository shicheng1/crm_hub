<template>
  <div>
    <div style="display: flex; justify-content: space-between; margin-bottom: 16px;">
      <h3>工单列表</h3>
      <el-button type="primary" @click="$router.push('/orders/create')">
        <el-icon><Plus /></el-icon> 创建工单
      </el-button>
    </div>

    <el-card>
      <div style="margin-bottom: 16px; display: flex; gap: 12px;">
        <el-input v-model="titleSearch" placeholder="按标题搜索" clearable style="width: 220px" @keyup.enter="search" @clear="search">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-select v-model="statusFilter" placeholder="状态筛选" clearable style="width: 160px" @change="search">
          <el-option label="待审批" :value="0" />
          <el-option label="审批中" :value="1" />
          <el-option label="已通过" :value="2" />
          <el-option label="已驳回" :value="3" />
          <el-option label="已关闭" :value="4" />
          <el-option label="退回修改" :value="5" />
        </el-select>
        <el-button @click="search">查询</el-button>
      </div>

      <el-table :data="orders" stripe v-loading="loading">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="title" label="标题" min-width="180" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="currentStep" label="当前步骤" width="100">
          <template #default="{ row }">{{ row.currentStep || '-' }}</template>
        </el-table-column>
        <el-table-column prop="creatorName" label="创建人" width="120">
          <template #default="{ row }">{{ row.creatorName || ('ID:' + row.creatorId) }}</template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="180" />
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button type="primary" link @click="$router.push(`/orders/${row.id}`)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination style="margin-top: 16px; justify-content: flex-end;"
          v-model:current-page="page" v-model:page-size="size" :total="total" :page-sizes="[10,20,50]"
          layout="total, sizes, prev, pager, next" @current-change="loadOrders" @size-change="loadOrders" />
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getOrderPage } from '../api/order'

const orders = ref([])
const page = ref(1)
const size = ref(10)
const total = ref(0)
const statusFilter = ref(null)
const titleSearch = ref('')
const loading = ref(false)

const statusText = (s) => ({ 0:'待审批', 1:'审批中', 2:'已通过', 3:'已驳回', 4:'已关闭', 5:'退回修改' }[s] || '未知')
const statusType = (s) => ({ 0:'warning', 1:'', 2:'success', 3:'danger', 4:'info', 5:'warning' }[s] || 'info')

const search = () => {
  page.value = 1
  loadOrders()
}

const loadOrders = async () => {
  loading.value = true
  try {
    const params = { page: page.value, size: size.value }
    if (statusFilter.value !== null && statusFilter.value !== '') params.status = statusFilter.value
    if (titleSearch.value.trim()) params.title = titleSearch.value.trim()
    const res = await getOrderPage(params)
    orders.value = res.data.records
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

onMounted(loadOrders)
</script>
