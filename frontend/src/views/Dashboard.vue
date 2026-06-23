<template>
  <div>
    <h3>数据看板</h3>

    <!-- 数字卡片 -->
    <el-row :gutter="16" style="margin-bottom: 20px;">
      <el-col :span="6" v-for="card in cards" :key="card.label">
        <el-card shadow="hover">
          <div style="text-align: center;">
            <div style="font-size: 28px; font-weight: bold; color: #409eff;">{{ stats[card.key] || 0 }}</div>
            <div style="color: #999; margin-top: 8px;">{{ card.label }}</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 状态分布 -->
    <el-row :gutter="16">
      <el-col :span="12">
        <el-card>
          <template #header>工单状态分布</template>
          <div v-for="item in statusCards" :key="item.key" style="display: flex; justify-content: space-between; padding: 8px 0; border-bottom: 1px solid #f0f0f0;">
            <span><el-tag :type="item.type" size="small">{{ item.label }}</el-tag></span>
            <span style="font-weight: bold;">{{ stats[item.key] || 0 }}</span>
          </div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card>
          <template #header>近 7 天趋势</template>
          <div v-if="trend.dates" style="display: flex; justify-content: space-between; align-items: flex-end; height: 200px; padding: 0 10px;">
            <div v-for="(date, i) in trend.dates" :key="i" style="text-align: center; flex: 1;">
              <el-tooltip :content="'新增: ' + (trend.newCounts[i]||0) + '  通过: ' + (trend.approveCounts[i]||0)">
                <div style="display: flex; flex-direction: column; align-items: center; gap: 4px;">
                  <div style="background: #409eff; width: 20px; border-radius: 2px;" :style="{ height: barHeight(trend.newCounts[i]) + 'px' }"></div>
                  <div style="background: #67c23a; width: 20px; border-radius: 2px;" :style="{ height: barHeight(trend.approveCounts[i]) + 'px' }"></div>
                </div>
              </el-tooltip>
              <div style="font-size: 11px; color: #999; margin-top: 4px;">{{ date }}</div>
            </div>
          </div>
          <div style="margin-top: 8px; font-size: 12px; color: #999;">
            <span style="color: #409eff;">■</span> 新增 &nbsp;
            <span style="color: #67c23a;">■</span> 通过
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getStats, getTrend } from '../api/dashboard'

const stats = ref({})
const trend = ref({})

const cards = [
  { key: 'total', label: '工单总数' },
  { key: 'todayNew', label: '今日新增' },
  { key: 'pending', label: '待审批' },
  { key: 'todayApproved', label: '今日通过' },
]

const statusCards = [
  { key: 'pending', label: '待审批', type: 'warning' },
  { key: 'reviewing', label: '审批中', type: '' },
  { key: 'approved', label: '已通过', type: 'success' },
  { key: 'rejected', label: '已驳回', type: 'danger' },
  { key: 'returned', label: '退回修改', type: 'warning' },
  { key: 'closed', label: '已关闭', type: 'info' },
]

const barHeight = (val) => {
  const max = Math.max(...(trend.value.newCounts || [1]), 1)
  return Math.max(4, (val / max) * 150)
}

onMounted(async () => {
  const [s, t] = await Promise.all([getStats(), getTrend()])
  stats.value = s.data
  trend.value = t.data
})
</script>
