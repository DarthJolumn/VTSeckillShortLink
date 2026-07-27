<template>
  <Teleport to="body">
    <div v-if="visible" class="kick-mask">
      <div class="kick-dialog card">
        <div class="kick-icon">⚠️</div>
        <h3>您已被迫下线</h3>
        <p>您的账号在另一台设备登录，或当前设备已被移除。</p>
        <button class="btn-primary" @click="emit('relogin')">重新登录</button>
      </div>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
defineProps<{ visible: boolean }>()
const emit = defineEmits<{ relogin: [] }>()
</script>

<style scoped>
.kick-mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.85);
  z-index: 10000;
  display: flex;
  align-items: center;
  justify-content: center;
}
.kick-dialog {
  padding: 40px 48px;
  text-align: center;
  max-width: 360px;
}
.kick-icon { font-size: 40px; margin-bottom: 12px; }
h3 { margin-bottom: 8px; color: var(--accent-gold); }
p { color: var(--text-secondary); font-size: 14px; margin-bottom: 24px; line-height: 1.6; }
</style>
