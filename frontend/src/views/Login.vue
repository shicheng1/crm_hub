<template>
  <div class="login-page">
    <el-card class="login-card" shadow="never">
      <div class="login-brand">
        <div class="login-brand__mark">审</div>
        <div>
          <div class="login-brand__title">工单审批系统</div>
          <div class="login-brand__sub">Order Flow Approval</div>
        </div>
      </div>
      <el-form :model="form" @submit.prevent="handleLogin">
        <el-form-item>
          <el-input v-model="form.username" placeholder="用户名" prefix-icon="User" />
        </el-form-item>
        <el-form-item>
          <el-input v-model="form.password" type="password" placeholder="密码" prefix-icon="Lock" show-password />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" style="width: 100%" :loading="loading" native-type="submit">
            登录
          </el-button>
        </el-form-item>
      </el-form>
      <div class="login-tip">
        测试账号：admin/123456（审批人）| user1/123456（普通用户）
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { login } from '../api/auth'
import { setRefreshToken, setToken, setUser } from '../utils/auth'

const router = useRouter()
const loading = ref(false)
const form = reactive({ username: '', password: '' })

const handleLogin = async () => {
  if (!form.username || !form.password) {
    ElMessage.warning('请输入用户名和密码')
    return
  }
  loading.value = true
  try {
    const res = await login(form)
    setToken(res.data.token)
    setRefreshToken(res.data.refreshToken)
    setUser({ userId: res.data.userId, username: res.data.username, role: res.data.role })
    ElMessage.success('登录成功')
    router.push('/dashboard')
  } catch (e) {
    // 错误已在拦截器处理
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 100vh;
  background: linear-gradient(135deg, #1e293b 0%, #0f172a 100%);
}
.login-card {
  width: 400px;
  padding: 8px 8px 16px;
}
.login-brand {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 24px;
}
.login-brand__mark {
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 10px;
  background: var(--app-primary);
  color: #fff;
  font-weight: 700;
  font-size: 20px;
}
.login-brand__title {
  font-size: 18px;
  font-weight: 700;
  color: var(--app-text);
}
.login-brand__sub {
  margin-top: 2px;
  color: var(--app-muted);
  font-size: 12px;
}
.login-tip {
  color: var(--app-muted);
  font-size: 12px;
  text-align: center;
  margin-top: 16px;
}
</style>
