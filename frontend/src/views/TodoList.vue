<template>
  <div>
    <h3>待我审批</h3>
    <el-card>
      <el-table :data="orders" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="title" label="标题" min-width="200" />
        <el-table-column prop="creatorName" label="创建人" width="100" />
        <el-table-column prop="currentStep" label="当前步骤" width="100" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? '' : 'warning'">{{ row.status === 1 ? '审批中' : '待审批' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="180" />
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button type="primary" link @click="$router.push(`/orders/${row.id}`)">去审批</el-button>
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
import { getTodoList } from '../api/order'

const orders = ref([])
const page = ref(1)
const total = ref(0)

const load = async () => {
  const res = await getTodoList({ page: page.value, size: 10 })
  orders.value = res.data.records
  total.value = res.data.total
}

onMounted(load)
</script>
