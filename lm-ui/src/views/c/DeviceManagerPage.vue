<template>
  <div class="devices-page">
    <div class="card panel">
      <h3>设备管理</h3>
      <p class="tip">以下设备已登录您的账号，移除后该设备将被强制下线。</p>

      <div v-if="userStore.devices.length === 0" class="empty">暂无设备</div>

      <div v-for="d in userStore.devices" :key="d.deviceId" class="device-row">
        <div class="device-main">
          <div class="device-id">
            📱 {{ shortId(d.deviceId) }}
            <span v-if="d.current" class="current-badge">当前设备</span>
          </div>
        </div>
        <button v-if="!d.current" class="btn-ghost kick-btn" @click="onKick(d.deviceId)">踢下线</button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useUserStore } from '@/stores/user'
import { showToast } from '@/utils/toast'
import { ApiError } from '@/utils/http'

const userStore = useUserStore()

onMounted(async () => {
  try {
    await userStore.fetchDevices()
  } catch { /* 401 由拦截器处理 */ }
})

function shortId(id: string) {
  return id.length > 18 ? id.slice(0, 8) + '...' + id.slice(-6) : id
}

async function onKick(deviceId: string) {
  try {
    await userStore.kickDevice(deviceId)
    showToast('设备已下线', 'success')
  } catch (e) {
    showToast(e instanceof ApiError ? e.message : '操作失败', 'error')
  }
}
</script>

<style scoped>
.panel { padding: 20px 24px; }
h3 { font-size: 16px; margin-bottom: 8px; }
.tip { font-size: 12px; color: var(--text-secondary); margin-bottom: 16px; }
.empty { text-align: center; color: var(--text-secondary); padding: 40px 0; font-size: 13px; }
.device-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 0;
  border-bottom: 1px solid var(--border);
}
.device-row:last-child { border-bottom: none; }
.device-id { font-family: var(--font-mono); font-size: 13px; display: flex; align-items: center; gap: 8px; }
.current-badge {
  font-size: 11px;
  color: var(--status-green);
  border: 1px solid var(--status-green);
  padding: 1px 8px;
  border-radius: 10px;
}
.kick-btn { padding: 5px 14px; font-size: 12px; color: var(--accent-red); border-color: var(--accent-red); }
</style>
