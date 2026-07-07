<template>
  <div>
    <h3>我已审批</h3>
    <el-card>
      <el-table :data="orders" stripe row-key="id">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="title" label="标题" min-width="200" />
        <el-table-column prop="creatorName" label="创建人" width="100" />
        <el-table-column prop="currentStep" label="当前步骤" width="100" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="180" />
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button type="primary" link @click="$router.push(`/orders/${row.id}`)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination style="margin-top: 16px; justify-content: flex-end;"
          v-model:current-page="page" :total="total" :page-size="10"
          layout="total, prev, pager, next" @current-change="load" />
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getDoneList } from '../api/order'

const orders = ref([])
const page = ref(1)
const total = ref(0)

const statusText = (s) => ({ 0:'待审批', 1:'审批中', 2:'已通过', 3:'已驳回', 4:'已关闭', 5:'退回修改' }[s] || '未知')
const statusType = (s) => ({ 0:'warning', 1:'', 2:'success', 3:'danger', 4:'info', 5:'warning' }[s] || 'info')

const load = async () => {
  const res = await getDoneList({ page: page.value, size: 10 })
  orders.value = res.data.records
  total.value = res.data.total
}

onMounted(load)
</script>
