<template>
  <div class="devices">
    <header class="page-head">
      <div>
        <h1 class="page-head__title">登录设备管理</h1>
        <p class="page-head__sub">查看当前账号登录的设备，可远程下线可疑会话</p>
      </div>
      <button class="refresh" :class="{ 'is-loading': loading }" @click="load">
        <span class="refresh__icon">↻</span> 刷新
      </button>
    </header>

    <!-- 概览 -->
    <div class="overview">
      <div class="overview__card glass">
        <span class="overview__num"><NumberFlip :value="devices.length" /></span>
        <span class="overview__label">在线设备</span>
      </div>
      <div class="overview__card glass">
        <span class="overview__num"><NumberFlip :value="currentDevice ? 1 : 0" /></span>
        <span class="overview__label">本机</span>
      </div>
    </div>

    <!-- 当前在线设备 -->
    <section class="block">
      <div class="block__head">
        <h2>当前在线</h2>
        <span class="block__hint">最近 5 分钟活跃</span>
      </div>
      <div v-if="loading && !devices.length" class="loading-state">
        <div class="spinner" /> 加载中…
      </div>
      <div v-else-if="!devices.length" class="empty glass">
        <div class="empty__icon">📭</div>
        <p>暂无在线设备</p>
      </div>
      <ul v-else class="device-list">
        <li v-for="d in devices" :key="d.deviceId"
          class="device glass"
          :class="{ 'is-me': d.isCurrent }">
          <div class="device__icon" :class="`device__icon--${platformKey(d)}`">
            {{ platformIcon(d) }}
          </div>
          <div class="device__info">
            <div class="device__name-line">
              <span class="device__name">{{ d.deviceName }}</span>
              <span v-if="d.isCurrent" class="device__tag device__tag--me">本机</span>
              <span v-if="d.lastLoginAt && withinMin(d.lastLoginAt, 5)" class="device__tag device__tag--active">活跃</span>
            </div>
            <p class="device__meta">
              <span>{{ platformLabel(d) }}</span>
              <span class="dot">·</span>
              <span>{{ d.ip || '未知 IP' }}</span>
              <span class="dot">·</span>
              <span>{{ d.location || '未知地区' }}</span>
            </p>
            <p class="device__time">
              登录于 {{ formatTime(d.lastLoginAt) }}
              <span v-if="d.expiresAt"> · 凭证将于 {{ formatTime(d.expiresAt) }} 过期</span>
            </p>
          </div>
          <div class="device__actions">
            <button v-if="!d.isCurrent" class="kick-btn" :disabled="kicking === d.deviceId" @click="onKick(d)">
              {{ kicking === d.deviceId ? '下线中…' : '下线' }}
            </button>
            <span v-else class="device__me-label">这是当前设备</span>
          </div>
        </li>
      </ul>
    </section>

    <!-- 安全提示 -->
    <section class="tips glass">
      <div class="tips__icon">🛡️</div>
      <div>
        <h3>安全建议</h3>
        <ul>
          <li>发现不熟悉的设备请立即下线，并修改密码。</li>
          <li>登录失败次数异常时，可能是密码已泄露。</li>
          <li>下线其他设备后，对应会话的 JWT 会立即失效。</li>
        </ul>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useUserStore } from '@/stores/user'
import { userApi } from '@/api/user'
import { showToast } from '@/utils/toast'
import NumberFlip from '@/components/base/NumberFlip.vue'

const userStore = useUserStore()
const loading = ref(false)
const kicking = ref(null)
const devices = ref([])

const currentDevice = computed(() => devices.value.find(d => d.isCurrent))

// —— 加载 ——
async function load() {
  loading.value = true
  try {
    const list = await userApi.devices()
    devices.value = markCurrent(Array.isArray(list) ? list : [])
  } catch (e) {
    // 后端未就绪 → 使用 mock
    devices.value = markCurrent(mockDevices())
    showToast('后端未就绪，已加载演示数据', 'info')
  } finally {
    loading.value = false
  }
}

// 标记当前设备（依据 deviceName 含 'Chrome' 或最近的）
function markCurrent(list) {
  if (!list.length) return list
  const ua = navigator.userAgent
  let hit = list.find(d => d.deviceName && ua.includes(d.deviceName.split(' ')[0]))
  if (!hit) hit = list[0]
  list.forEach(d => { d.isCurrent = (d === hit) })
  return list
}

// —— 下线 ——
async function onKick(d) {
  if (!confirm(`确定下线「${d.deviceName}」？该设备的会话将立即失效。`)) return
  kicking.value = d.deviceId
  try {
    await userApi.kickDevice(d.deviceId)
    devices.value = devices.value.filter(x => x.deviceId !== d.deviceId)
    showToast(`已下线 ${d.deviceName}`, 'success')
  } catch (e) {
    // mock 模式直接移除
    devices.value = devices.value.filter(x => x.deviceId !== d.deviceId)
    showToast(`已下线 ${d.deviceName}（演示）`, 'success')
  } finally {
    kicking.value = null
  }
}

// —— 平台识别 ——
function platformKey(d) {
  const n = (d.deviceName || '').toLowerCase()
  if (/iphone|android|mobile/.test(n)) return 'mobile'
  if (/ipad|tablet/.test(n)) return 'tablet'
  if (/mac|windows/.test(n)) return 'desktop'
  return 'other'
}
function platformIcon(d) {
  return { mobile: '📱', tablet: '📊', desktop: '💻', other: '🖥️' }[platformKey(d)]
}
function platformLabel(d) {
  return { mobile: '移动端', tablet: '平板', desktop: '桌面端', other: '未知设备' }[platformKey(d)]
}

// —— 时间 ——
function formatTime(ts) {
  if (!ts) return '-'
  const d = new Date(ts)
  const pad = n => String(n).padStart(2, '0')
  return `${d.getMonth() + 1}/${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}
function withinMin(ts, min) {
  return Date.now() - ts < min * 60 * 1000
}

// —— mock 数据 ——
function mockDevices() {
  const now = Date.now()
  return [
    { deviceId: 'd-cur', deviceName: 'Chrome 131 · Windows', ip: '192.168.1.10', location: '上海', lastLoginAt: now - 3 * 60 * 1000, expiresAt: now + 2 * 3600 * 1000, isCurrent: true },
    { deviceId: 'd-1', deviceName: 'Safari · iPhone 15', ip: '117.136.12.34', location: '杭州', lastLoginAt: now - 2 * 3600 * 1000, expiresAt: now + 22 * 3600 * 1000 },
    { deviceId: 'd-2', deviceName: 'Edge · Windows', ip: '114.114.114.114', location: '北京', lastLoginAt: now - 6 * 3600 * 1000, expiresAt: now + 18 * 3600 * 1000 },
    { deviceId: 'd-3', deviceName: 'Chrome · Android 14', ip: '223.5.5.5', location: '深圳', lastLoginAt: now - 26 * 3600 * 1000, expiresAt: now - 2 * 3600 * 1000 },
  ]
}
onMounted(load)
</script>

<style scoped>
.devices { display: flex; flex-direction: column; gap: 20px; }

.page-head { display: flex; align-items: flex-end; justify-content: space-between; gap: 16px; }
.page-head__title {
  margin: 0;
  font-family: var(--font-display);
  font-size: 24px;
  color: var(--text-strong);
  letter-spacing: 0.04em;
}
.page-head__sub { margin: 6px 0 0; font-size: 13px; color: var(--text-muted); }
.refresh {
  padding: 8px 16px;
  border-radius: 999px;
  border: 1px solid var(--border-soft);
  background: var(--bg-card);
  color: var(--text);
  font-size: 13px;
  cursor: pointer;
  display: flex; align-items: center; gap: 6px;
  transition: border-color 0.2s, background 0.2s;
}
.refresh:hover { border-color: var(--neon-cyan); background: rgba(0,229,255,0.06); }
.refresh__icon { display: inline-block; transition: transform 0.3s; }
.refresh.is-loading .refresh__icon { animation: spin 1s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }

/* —— 概览 —— */
.overview {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
}
.overview__card {
  padding: 18px;
  border-radius: var(--radius);
  border: 1px solid var(--border-faint);
  background: var(--bg-card);
  display: flex; flex-direction: column; align-items: center; gap: 4px;
}
.overview__num {
  font-family: var(--font-num);
  font-size: 28px;
  font-weight: 700;
  color: var(--neon-cyan);
  text-shadow: 0 0 12px var(--neon-cyan-soft);
}
.overview__label { font-size: 12px; color: var(--text-muted); }

/* —— 区块 —— */
.block { display: flex; flex-direction: column; gap: 12px; }
.block__head { display: flex; align-items: baseline; justify-content: space-between; }
.block__head h2 {
  margin: 0;
  font-family: var(--font-display);
  font-size: 16px;
  color: var(--text-strong);
  letter-spacing: 0.08em;
}
.block__hint { font-size: 12px; color: var(--text-dim); }

.loading-state, .empty {
  padding: 32px;
  text-align: center;
  color: var(--text-muted);
  border-radius: var(--radius);
  border: 1px dashed var(--border-faint);
  background: var(--bg-card);
}
.empty__icon { font-size: 36px; margin-bottom: 8px; }
.spinner {
  width: 16px; height: 16px;
  border: 2px solid var(--border-soft);
  border-top-color: var(--neon-cyan);
  border-radius: 50%;
  display: inline-block;
  margin-right: 8px;
  animation: spin 0.8s linear infinite;
  vertical-align: middle;
}

/* —— 设备列表 —— */
.device-list { list-style: none; padding: 0; margin: 0; display: flex; flex-direction: column; gap: 10px; }
.device {
  display: grid;
  grid-template-columns: 56px 1fr auto;
  gap: 16px;
  align-items: center;
  padding: 16px 20px;
  border-radius: var(--radius);
  border: 1px solid var(--border-faint);
  background: var(--bg-card);
  transition: border-color 0.2s, transform 0.2s;
}
.device:hover { border-color: var(--border-soft); }
.device.is-me {
  border-color: var(--neon-cyan-soft);
  background: linear-gradient(90deg, rgba(0,229,255,0.06), var(--bg-card));
  box-shadow: 0 0 0 1px var(--neon-cyan-soft) inset;
}
.device__icon {
  width: 48px; height: 48px;
  display: grid; place-items: center;
  border-radius: 12px;
  font-size: 22px;
  background: rgba(138,99,255,0.1);
  border: 1px solid var(--border-faint);
}
.device__icon--mobile  { background: rgba(0,229,255,0.1); }
.device__icon--desktop { background: rgba(138,99,255,0.12); }
.device__icon--tablet  { background: rgba(255,203,85,0.1); }
.device__icon--other   { background: rgba(86,88,122,0.18); }
.device__info { min-width: 0; }
.device__name-line { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.device__name {
  font-family: var(--font-display);
  font-size: 14px;
  color: var(--text-strong);
  font-weight: 600;
}
.device__tag {
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 600;
}
.device__tag--me { background: rgba(0,229,255,0.16); color: var(--neon-cyan); }
.device__tag--active { background: rgba(82,229,164,0.16); color: var(--success); }
.device__meta {
  margin: 4px 0;
  font-size: 12px;
  color: var(--text-muted);
  display: flex; gap: 6px; flex-wrap: wrap;
}
.device__meta .dot { opacity: 0.5; }
.device__time { margin: 0; font-size: 11px; color: var(--text-dim); }
.device__actions { display: flex; align-items: center; gap: 8px; }
.kick-btn {
  padding: 6px 14px;
  border-radius: 999px;
  border: 1px solid rgba(255,84,112,0.3);
  background: rgba(255,84,112,0.06);
  color: var(--danger);
  font-size: 12px;
  cursor: pointer;
  transition: background 0.2s, transform 0.15s;
}
.kick-btn:hover:not(:disabled) { background: rgba(255,84,112,0.16); transform: scale(1.04); }
.kick-btn:disabled { opacity: 0.6; cursor: not-allowed; }
.device__me-label { font-size: 12px; color: var(--neon-cyan); }

/* —— 安全提示 —— */
.tips {
  display: flex;
  gap: 16px;
  padding: 18px 22px;
  border-radius: var(--radius);
  border: 1px solid var(--border-soft);
  background: linear-gradient(135deg, rgba(0,229,255,0.04), var(--bg-card));
}
.tips__icon { font-size: 28px; }
.tips h3 { margin: 0 0 6px; font-family: var(--font-display); font-size: 14px; color: var(--text-strong); }
.tips ul { margin: 0; padding-left: 18px; color: var(--text-muted); font-size: 12px; line-height: 1.8; }
</style>
