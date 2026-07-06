<template>
  <div>
    <div style="display: flex; justify-content: space-between; margin-bottom: 16px;">
      <h3>用户管理</h3>
      <el-button type="primary" @click="openCreate"><el-icon><Plus /></el-icon> 新建用户</el-button>
    </div>

    <el-card style="margin-bottom: 16px;">
      <el-form :inline="true" :model="query">
        <el-form-item label="用户名">
          <el-input v-model="query.username" placeholder="用户名" clearable />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="query.role" clearable placeholder="全部" style="width: 140px">
            <el-option label="管理员" value="ADMIN" />
            <el-option label="审批人" value="APPROVER" />
            <el-option label="普通用户" value="USER" />
          </el-select>
        </el-form-item>
        <el-form-item label="部门">
          <el-select v-model="query.deptId" clearable placeholder="全部" style="width: 160px">
            <el-option v-for="d in departments" :key="d.id" :label="d.name" :value="d.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" clearable placeholder="全部" style="width: 120px">
            <el-option label="启用" :value="1" />
            <el-option label="禁用" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadUsers">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card>
      <el-table :data="users" v-loading="loading" border>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="username" label="用户名" min-width="140" />
        <el-table-column label="角色" width="120">
          <template #default="{ row }">
            <el-tag :type="roleTag(row.role)">{{ roleText(row.role) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="deptName" label="部门" width="140" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '启用' : '禁用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="180" />
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="warning" @click="openResetPassword(row)">重置密码</el-button>
            <el-button link :type="row.status === 1 ? 'danger' : 'success'" @click="toggleStatus(row)">
              {{ row.status === 1 ? '禁用' : '启用' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <div style="margin-top: 16px; text-align: right;">
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.size"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @size-change="loadUsers"
          @current-change="loadUsers"
        />
      </div>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑用户' : '新建用户'" width="520px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="用户名" required>
          <el-input v-model="form.username" :disabled="!!editingId" placeholder="用户名" />
        </el-form-item>
        <el-form-item v-if="!editingId" label="密码" required>
          <el-input v-model="form.password" type="password" show-password placeholder="初始密码" />
        </el-form-item>
        <el-form-item label="角色" required>
          <el-select v-model="form.role" style="width: 100%">
            <el-option label="管理员" value="ADMIN" />
            <el-option label="审批人" value="APPROVER" />
            <el-option label="普通用户" value="USER" />
          </el-select>
        </el-form-item>
        <el-form-item label="部门">
          <el-select v-model="form.deptId" clearable placeholder="选择部门" style="width: 100%">
            <el-option v-for="d in departments" :key="d.id" :label="d.name" :value="d.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio-button :label="1">启用</el-radio-button>
            <el-radio-button :label="0">禁用</el-radio-button>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitUser">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="passwordDialogVisible" title="重置密码" width="420px">
      <el-form label-width="90px">
        <el-form-item label="用户">
          <span>{{ passwordUser?.username }}</span>
        </el-form-item>
        <el-form-item label="新密码" required>
          <el-input v-model="newPassword" type="password" show-password placeholder="请输入新密码" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="passwordDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitResetPassword">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { getDeptList } from '../api/dept'
import { createUser, getUserPage, resetUserPassword, updateUser, updateUserStatus } from '../api/user'

const loading = ref(false)
const submitting = ref(false)
const users = ref([])
const departments = ref([])
const total = ref(0)
const dialogVisible = ref(false)
const passwordDialogVisible = ref(false)
const editingId = ref(null)
const passwordUser = ref(null)
const newPassword = ref('')
const query = ref({ page: 1, size: 10, username: '', role: '', deptId: null, status: null })
const form = ref({ username: '', password: '', role: 'USER', deptId: null, status: 1 })

const roleText = (role) => ({ ADMIN: '管理员', APPROVER: '审批人', USER: '普通用户' }[role] || role)
const roleTag = (role) => ({ ADMIN: 'danger', APPROVER: 'warning', USER: 'info' }[role] || 'info')

const loadUsers = async () => {
  loading.value = true
  try {
    const params = { ...query.value }
    Object.keys(params).forEach(k => (params[k] === '' || params[k] === null) && delete params[k])
    const res = await getUserPage(params)
    users.value = res.data.records || []
    total.value = res.data.total || 0
  } finally {
    loading.value = false
  }
}

const loadDepartments = async () => {
  const res = await getDeptList()
  departments.value = res.data || []
}

const resetQuery = () => {
  query.value = { page: 1, size: 10, username: '', role: '', deptId: null, status: null }
  loadUsers()
}

const openCreate = () => {
  editingId.value = null
  form.value = { username: '', password: '', role: 'USER', deptId: null, status: 1 }
  dialogVisible.value = true
}

const openEdit = (row) => {
  editingId.value = row.id
  form.value = { username: row.username, password: '', role: row.role, deptId: row.deptId, status: row.status }
  dialogVisible.value = true
}

const submitUser = async () => {
  if (!form.value.username.trim()) return ElMessage.warning('请输入用户名')
  if (!editingId.value && !form.value.password.trim()) return ElMessage.warning('请输入密码')
  if (!form.value.role) return ElMessage.warning('请选择角色')

  submitting.value = true
  try {
    if (editingId.value) {
      await updateUser(editingId.value, { role: form.value.role, deptId: form.value.deptId, status: form.value.status })
    } else {
      await createUser(form.value)
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    await loadUsers()
  } finally {
    submitting.value = false
  }
}

const openResetPassword = (row) => {
  passwordUser.value = row
  newPassword.value = ''
  passwordDialogVisible.value = true
}

const submitResetPassword = async () => {
  if (!newPassword.value.trim()) return ElMessage.warning('请输入新密码')
  submitting.value = true
  try {
    await resetUserPassword(passwordUser.value.id, newPassword.value)
    ElMessage.success('密码已重置')
    passwordDialogVisible.value = false
  } finally {
    submitting.value = false
  }
}

const toggleStatus = async (row) => {
  const nextStatus = row.status === 1 ? 0 : 1
  await ElMessageBox.confirm(`确定${nextStatus === 1 ? '启用' : '禁用'}用户「${row.username}」吗？`, '确认操作')
  await updateUserStatus(row.id, nextStatus)
  ElMessage.success('状态已更新')
  await loadUsers()
}

onMounted(async () => {
  await Promise.all([loadDepartments(), loadUsers()])
})
</script>
