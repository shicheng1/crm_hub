<template>
  <div>
    <div class="page-header">
      <div>
        <h3 class="page-title">数据看板</h3>
        <div class="page-subtitle">工单处理概览、状态分布与近 7 天趋势</div>
      </div>
    </div>

    <div class="stat-grid">
      <el-card v-for="card in cards" :key="card.label" class="stat-card" shadow="never">
        <div class="stat-card__body">
          <div class="stat-card__icon" :class="card.className">
            <el-icon><component :is="card.icon" /></el-icon>
          </div>
          <div>
            <div class="stat-card__value">{{ stats[card.key] || 0 }}</div>
            <div class="stat-card__label">{{ card.label }}</div>
          </div>
        </div>
      </el-card>
    </div>

    <el-row :gutter="18">
      <el-col :span="10">
        <el-card class="content-card" shadow="never">
          <template #header>
            <div class="card-header">工单状态分布</div>
          </template>
          <div class="status-list">
            <div v-for="item in statusCards" :key="item.key" class="status-row">
              <div class="status-row__label">
                <span class="status-dot" :class="item.className"></span>
                <span>{{ item.label }}</span>
              </div>
              <div class="status-row__value">{{ stats[item.key] || 0 }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="14">
        <el-card class="content-card" shadow="never">
          <template #header>
            <div class="card-header">近 7 天趋势</div>
          </template>
          <div v-if="trend.dates" class="trend-chart">
            <div v-for="(date, i) in trend.dates" :key="i" class="trend-item">
              <el-tooltip :content="'新增: ' + (trend.newCounts[i] || 0) + '  通过: ' + (trend.approveCounts[i] || 0)">
                <div class="trend-bars">
                  <div class="trend-bar trend-bar--new" :style="{ height: barHeight(trend.newCounts[i]) + 'px' }"></div>
                  <div class="trend-bar trend-bar--approved" :style="{ height: barHeight(trend.approveCounts[i]) + 'px' }"></div>
                </div>
              </el-tooltip>
              <div class="trend-label">{{ date }}</div>
            </div>
          </div>
          <el-empty v-else description="暂无趋势数据" :image-size="80" />
          <div class="chart-legend">
            <span><i class="legend-dot legend-dot--new"></i>新增</span>
            <span><i class="legend-dot legend-dot--approved"></i>通过</span>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { CircleCheck, DataAnalysis, Document, Timer } from '@element-plus/icons-vue'
import { getStats, getTrend } from '../api/dashboard'

const stats = ref({})
const trend = ref({})

const cards = [
  { key: 'total', label: '工单总数', icon: Document, className: 'is-blue' },
  { key: 'todayNew', label: '今日新增', icon: DataAnalysis, className: 'is-green' },
  { key: 'pending', label: '待审批', icon: Timer, className: 'is-amber' },
  { key: 'todayApproved', label: '今日通过', icon: CircleCheck, className: 'is-purple' },
]

const statusCards = [
  { key: 'pending', label: '待审批', className: 'is-amber' },
  { key: 'reviewing', label: '审批中', className: 'is-blue' },
  { key: 'approved', label: '已通过', className: 'is-green' },
  { key: 'rejected', label: '已驳回', className: 'is-red' },
  { key: 'returned', label: '退回修改', className: 'is-purple' },
  { key: 'closed', label: '已关闭', className: 'is-gray' },
]

// 全局最大值提到 computed，避免每个柱子（14 次）重复 Math.max 展开全部数组
const trendMax = computed(() => {
  const vals = [...(trend.value.newCounts || []), ...(trend.value.approveCounts || []), 1]
  return Math.max(...vals)
})

const barHeight = (val) => {
  return Math.max(6, (Number(val || 0) / trendMax.value) * 148)
}

onMounted(async () => {
  const [s, t] = await Promise.all([getStats(), getTrend()])
  stats.value = s.data
  trend.value = t.data
})
</script>

<style scoped>
.card-header {
  font-size: 15px;
  font-weight: 650;
}

.stat-card__icon.is-blue {
  color: #2563eb;
  background: #eff6ff;
}

.stat-card__icon.is-green {
  color: #16a34a;
  background: #ecfdf5;
}

.stat-card__icon.is-amber {
  color: #d97706;
  background: #fffbeb;
}

.stat-card__icon.is-purple {
  color: #7c3aed;
  background: #f5f3ff;
}

.status-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.status-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 2px;
  border-bottom: 1px solid var(--app-border);
}

.status-row:last-child {
  border-bottom: none;
}

.status-row__label {
  display: flex;
  align-items: center;
  gap: 9px;
  color: var(--app-subtle);
}

.status-row__value {
  font-size: 18px;
  font-weight: 700;
}

.status-dot,
.legend-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.is-blue {
  background: #2563eb;
}

.is-green {
  background: #16a34a;
}

.is-amber {
  background: #d97706;
}

.is-red {
  background: #dc2626;
}

.is-purple {
  background: #7c3aed;
}

.is-gray {
  background: #94a3b8;
}

.trend-chart {
  height: 224px;
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 12px;
  padding: 8px 8px 0;
}

.trend-item {
  flex: 1;
  min-width: 0;
  text-align: center;
}

.trend-bars {
  height: 164px;
  display: flex;
  align-items: flex-end;
  justify-content: center;
  gap: 5px;
}

.trend-bar {
  width: 14px;
  min-height: 6px;
  border-radius: 4px 4px 0 0;
}

.trend-bar--new {
  background: #2563eb;
}

.trend-bar--approved {
  background: #16a34a;
}

.trend-label {
  margin-top: 10px;
  color: var(--app-muted);
  font-size: 12px;
}

.chart-legend {
  display: flex;
  align-items: center;
  gap: 18px;
  margin-top: 12px;
  color: var(--app-subtle);
  font-size: 13px;
}

.chart-legend span {
  display: inline-flex;
  align-items: center;
  gap: 7px;
}

.legend-dot--new {
  background: #2563eb;
}

.legend-dot--approved {
  background: #16a34a;
}
</style>
