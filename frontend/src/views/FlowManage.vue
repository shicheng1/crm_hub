<template>
  <div>
    <div style="display: flex; justify-content: space-between; margin-bottom: 16px;">
      <h3>审批流管理</h3>
      <el-button type="primary" @click="openCreate">
        <el-icon><Plus /></el-icon> 新建审批流
      </el-button>
    </div>

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
            <div>模式：{{ step.approveMode === 'ALL' ? '会签' : '或签' }}</div>
            <div>审批人：{{ formatApprovers(step) }}</div>
          </template>
        </el-step>
      </el-steps>
    </el-card>

    <!-- 创建审批流对话框 -->
    <el-dialog v-model="dialogVisible" title="新建审批流" width="640px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="流程名称" required>
          <el-input v-model="form.name" placeholder="如：普通工单审批" />
        </el-form-item>
        <el-form-item label="流程描述">
          <el-input v-model="form.description" placeholder="可选" />
        </el-form-item>
        <el-form-item label="驳回策略" required>
          <el-select v-model="form.rejectMode" style="width: 100%">
            <el-option label="回到第一步（RESTART）" value="RESTART" />
            <el-option label="退回上一步（PREVIOUS）" value="PREVIOUS" />
            <el-option label="退回发起人（ORIGIN）" value="ORIGIN" />
          </el-select>
        </el-form-item>
        <el-divider>审批步骤</el-divider>
        <div v-for="(step, idx) in form.steps" :key="idx" style="margin-bottom: 16px; padding: 12px; background: #fafafa; border-radius: 4px;">
          <div style="display: flex; gap: 8px; margin-bottom: 8px;">
            <el-tag>步骤 {{ idx + 1 }}</el-tag>
            <el-input v-model="step.stepName" placeholder="步骤名称" style="flex: 1" />
            <el-select v-model="step.approveMode" placeholder="审批模式" style="width: 120px">
              <el-option label="或签" value="ANY" />
              <el-option label="会签" value="ALL" />
            </el-select>
            <el-button type="danger" link @click="removeStep(idx)" v-if="form.steps.length > 1">删除</el-button>
          </div>
          <el-select v-model="step.approverIds" multiple :placeholder="step.approveMode === 'ALL' ? '选择审批人（会签：全部通过才进入下一步）' : '选择审批人（或签：任一通过即可）'" style="width: 100%">
            <el-option v-for="u in users" :key="u.id" :label="u.username + ({ ADMIN: '（管理员）', APPROVER: '（审批人）', USER: '（普通用户）' }[u.role] || '')" :value="u.id" />
          </el-select>
        </div>
        <el-button @click="addStep" :icon="Plus">添加步骤</el-button>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { getFlowList, createFlow } from '../api/flow'
import { getUserList } from '../api/dept'

const flows = ref([])
const users = ref([])
const dialogVisible = ref(false)
const submitting = ref(false)
const form = ref({ name: '', description: '', rejectMode: 'ORIGIN', steps: [] })

const formatApprovers = (step) => {
  if (!step.approvers || step.approvers.length === 0) return '未配置'
  const separator = step.approveMode === 'ALL' ? ' 且 ' : ' 或 '
  return step.approvers.map(a => {
    const u = users.value.find(x => x.id === a.userId)
    return u ? u.username : ('ID:' + a.userId)
  }).join(separator)
}

const loadFlows = async () => {
  const res = await getFlowList()
  flows.value = res.data
}

const openCreate = () => {
  form.value = {
    name: '',
    description: '',
    rejectMode: 'ORIGIN',
    steps: [{ stepName: '', approveMode: 'ANY', approverIds: [] }]
  }
  dialogVisible.value = true
}

const addStep = () => {
  form.value.steps.push({ stepName: '', approveMode: 'ANY', approverIds: [] })
}

const removeStep = (idx) => {
  form.value.steps.splice(idx, 1)
}

const submit = async () => {
  if (!form.value.name.trim()) return ElMessage.warning('请输入流程名称')
  if (form.value.steps.length === 0) return ElMessage.warning('至少需要一个步骤')
  for (const s of form.value.steps) {
    if (!s.stepName.trim()) return ElMessage.warning('请填写所有步骤名称')
    if (s.approverIds.length === 0) return ElMessage.warning('每个步骤至少选择一个审批人')
  }
  submitting.value = true
  try {
    // 转换为后端结构
    const payload = {
      name: form.value.name,
      description: form.value.description,
      rejectMode: form.value.rejectMode,
      status: 1,
      steps: form.value.steps.map((s, i) => ({
        stepOrder: i + 1,
        stepName: s.stepName,
        approveMode: s.approveMode || 'ANY',
        approvers: s.approverIds.map(uid => ({ userId: uid }))
      }))
    }
    await createFlow(payload)
    ElMessage.success('创建成功')
    dialogVisible.value = false
    await loadFlows()
  } catch (e) {} finally {
    submitting.value = false
  }
}

onMounted(async () => {
  const [f, u] = await Promise.all([getFlowList(), getUserList()])
  flows.value = f.data
  users.value = u.data
})
</script>
