<template>
  <div>
    <h3>创建工单</h3>
    <el-card style="max-width: 600px;">
      <el-form :model="form" label-width="100px">
        <el-form-item label="审批流程" required>
          <el-select v-model="form.flowId" placeholder="请选择审批流程" style="width: 100%;">
            <el-option v-for="flow in flows" :key="flow.id" :label="flow.name" :value="flow.id">
              <span>{{ flow.name }}</span>
              <span style="color: #999; font-size: 12px; margin-left: 8px;">{{ flow.description }}</span>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="标题" required>
          <el-input v-model="form.title" placeholder="请输入工单标题" maxlength="200" show-word-limit />
        </el-form-item>
        <el-form-item label="内容">
          <el-input v-model="form.content" type="textarea" :rows="6" placeholder="请输入工单内容" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="handleSubmit">提交</el-button>
          <el-button @click="$router.back()">取消</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { createOrder } from '../api/order'
import { getFlowList } from '../api/flow'

const router = useRouter()
const loading = ref(false)
const flows = ref([])
const form = reactive({ title: '', content: '', flowId: null })

onMounted(async () => {
  const res = await getFlowList()
  flows.value = res.data
  if (flows.value.length > 0) {
    form.flowId = flows.value[0].id
  }
})

const handleSubmit = async () => {
  if (!form.title.trim()) return ElMessage.warning('请输入标题')
  if (!form.flowId) return ElMessage.warning('请选择审批流程')
  loading.value = true
  try {
    const res = await createOrder(form)
    ElMessage.success('工单创建成功')
    router.push(`/orders/${res.data}`)
  } catch (e) {} finally { loading.value = false }
}
</script>
