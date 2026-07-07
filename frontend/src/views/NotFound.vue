<template>
  <div class="not-found">
    <el-card class="not-found__card" shadow="never">
      <div class="not-found__mark">404</div>
      <div class="not-found__title">页面不存在</div>
      <div class="not-found__desc">你访问的页面已被移动或从未存在</div>
      <el-button type="primary" @click="$router.push('/dashboard')">返回首页</el-button>
    </el-card>
  </div>
</template>

<script setup>
</script>

<style scoped>
.not-found {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  background: var(--app-bg);
}
.not-found__card {
  width: 420px;
  text-align: center;
  padding: 16px;
}
.not-found__mark {
  font-size: 88px;
  font-weight: 800;
  line-height: 1;
  color: var(--app-primary);
  letter-spacing: 2px;
}
.not-found__title {
  margin-top: 12px;
  font-size: 20px;
  font-weight: 650;
  color: var(--app-text);
}
.not-found__desc {
  margin: 10px 0 24px;
  color: var(--app-subtle);
  font-size: 14px;
}
</style>
