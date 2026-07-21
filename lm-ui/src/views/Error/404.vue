<template>
  <div class="error-page">
    <div class="code">404</div>
    <p>页面不存在</p>
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
  color: var(--accent-red);
}
p { color: var(--text-secondary); }
</style>
