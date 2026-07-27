<template>
  <div class="error-page">
    <div class="code">403</div>
    <p>抱歉，您没有权限访问此页面</p>
    <router-link to="/" class="btn-primary">返回首页</router-link>
  </div>
</template>

<script setup lang="ts">
</script>

<style scoped>
.error-page {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16px;
}
.code {
  font-size: 80px;
  font-weight: 800;
  font-family: var(--font-mono);
  color: var(--accent-gold);
}
p { color: var(--text-secondary); }
</style>
