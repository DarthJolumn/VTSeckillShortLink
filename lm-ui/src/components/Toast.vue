<template>
  <Teleport to="body">
    <div class="toast-container">
      <TransitionGroup name="toast">
        <div v-for="t in toastState.list" :key="t.id" class="toast" :class="`toast-${t.type}`">
          {{ t.text }}
        </div>
      </TransitionGroup>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { toastState } from '@/utils/toast'
</script>

<style scoped>
.toast-container {
  position: fixed;
  top: 60px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 9999;
  display: flex;
  flex-direction: column;
  gap: 8px;
  pointer-events: none;
}
.toast {
  padding: 10px 20px;
  border-radius: 8px;
  font-size: 14px;
  background: var(--bg-card);
  border: 1px solid var(--border);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.4);
}
.toast-success { border-color: var(--status-green); color: var(--status-green); }
.toast-error { border-color: var(--accent-red); color: var(--accent-red); }
.toast-warning { border-color: var(--accent-gold); color: var(--accent-gold); }

.toast-enter-active, .toast-leave-active { transition: all 0.25s ease; }
.toast-enter-from { opacity: 0; transform: translateY(-10px); }
.toast-leave-to { opacity: 0; }
</style>
