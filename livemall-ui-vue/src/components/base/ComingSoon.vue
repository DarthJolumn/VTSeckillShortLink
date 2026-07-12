<template>
  <div class="coming">
    <div class="coming__ring" aria-hidden="true" />
    <h2 class="coming__title">{{ title }}</h2>
    <p class="coming__desc">{{ desc }}</p>
    <div class="coming__tag">{{ tag }}</div>
  </div>
</template>

<script setup>
defineProps({
  title: { type: String, default: '页面建设中' },
  desc: { type: String, default: '该模块按 前端设计方案.md §12 落地顺序逐步实现' },
  tag: { type: String, default: 'COMING SOON' },
})
</script>

<style scoped>
.coming {
  min-height: 60vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 14px;
  text-align: center;
  padding: 48px 24px;
  position: relative;
}
.coming__ring {
  width: 88px; height: 88px;
  border-radius: 50%;
  border: 2px solid transparent;
  border-top-color: var(--neon-purple);
  border-right-color: var(--neon-cyan);
  animation: radar-spin 2.4s linear infinite;
  box-shadow: 0 0 24px var(--neon-purple-soft);
  margin-bottom: 8px;
}
.coming__title { font-size: 28px; letter-spacing: 0.04em; }
.coming__desc { color: var(--text-muted); font-size: 14px; max-width: 460px; }
.coming__tag {
  font-family: var(--font-num);
  font-size: 11px;
  letter-spacing: 0.36em;
  color: var(--neon-cyan);
  padding: 5px 14px;
  border: 1px solid var(--border-strong);
  border-radius: 999px;
  background: var(--bg-card);
}
</style>
