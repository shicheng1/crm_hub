<template>
  <div style="display: flex; flex-direction: column; justify-content: center; align-items: center; height: 100vh; background: #f5f5f5;">
    <h1 style="font-size: 80px; color: #409eff; margin: 0;">404</h1>
    <p style="color: #666; margin: 16px 0 24px;">页面不存在</p>
    <el-button type="primary" @click="$router.push('/dashboard')">返回首页</el-button>
  </div>
</template>

<script setup>
</script>
