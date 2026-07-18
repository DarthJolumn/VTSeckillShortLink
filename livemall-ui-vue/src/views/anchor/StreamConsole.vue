<template>
  <div class="console">
    <!-- 顶部状态条 -->
    <header class="console__head glass">
      <div class="head__left">
        <span class="status-dot" :class="`status-dot--${streamPhase}`" />
        <span class="status-text">{{ statusLabel }}</span>
        <span v-if="live" class="status-time">已开播 {{ elapsedLabel }}</span>
      </div>
      <div class="head__right">
        <span v-if="live" class="head__metric">
          <span class="head__metric-label">在线</span>
          <NumberFlip :value="stats.online" />
        </span>
        <span v-if="live" class="head__metric">
          <span class="head__metric-label">本场收益</span>
          <span class="num">¥{{ stats.revenue.toFixed(0) }}</span>
        </span>
        <NeonButton v-if="!live" variant="primary" @click="onStart">开始直播</NeonButton>
        <NeonButton v-else variant="danger" @click="onStop">结束直播</NeonButton>
      </div>
    </header>

    <div class="console__body">
      <!-- 左：预览 + 设置 -->
      <section class="col col--left">
        <!-- 视频预览 -->
        <div class="preview glass" :class="{ 'is-live': live }">
          <video v-if="stream" ref="videoRef" autoplay muted playsinline />
          <div v-else class="preview__placeholder">
            <div class="preview__placeholder-bg" />
            <div class="preview__placeholder-content">
              <div class="preview__icon">{{ live ? '📡' : '🎥' }}</div>
              <p>{{ live ? '推流中…等待画面' : '点击「开始直播」开启摄像头' }}</p>
              <button v-if="!live" class="preview__btn" @click="onStart">开始直播</button>
            </div>
          </div>
          <!-- 直播角标 -->
          <div v-if="live" class="preview__badges">
            <span class="preview__badge preview__badge--live"><span class="dot" /> LIVE</span>
            <span class="preview__badge">1080P · 60fps</span>
            <span class="preview__badge">{{ bitRateLabel }}</span>
          </div>
          <!-- 互动浮层 -->
          <div v-if="live" class="preview__float">
            <div class="float__item" @click="onToggleMic">
              <span class="float__icon">{{ micOn ? '🎙️' : '🔇' }}</span>
              <span class="float__label">{{ micOn ? '麦克风' : '已静音' }}</span>
            </div>
            <div class="float__item" @click="onToggleCam">
              <span class="float__icon">{{ camOn ? '📹' : '🚫' }}</span>
              <span class="float__label">{{ camOn ? '摄像头' : '已关闭' }}</span>
            </div>
            <div class="float__item" @click="onShare">
              <span class="float__icon">🔗</span>
              <span class="float__label">分享</span>
            </div>
          </div>
        </div>

        <!-- 直播设置 -->
        <div class="panel glass">
          <div class="panel__head">
            <h3>直播设置</h3>
            <span class="panel__hint">{{ live ? '直播中（部分项不可改）' : '开播前可配置' }}</span>
          </div>
          <form class="form" @submit.prevent>
            <label class="field">
              <span class="field__label">直播标题</span>
              <input v-model.trim="form.title" type="text" maxlength="40" :disabled="live" placeholder="例如：深夜数码秒杀局" />
            </label>
            <div class="field-row">
              <label class="field">
                <span class="field__label">分类</span>
                <select v-model="form.category" :disabled="live">
                  <option value="digital">数码</option>
                  <option value="beauty">美妆</option>
                  <option value="food">食品</option>
                  <option value="fashion">服饰</option>
                  <option value="other">其他</option>
                </select>
              </label>
              <label class="field">
                <span class="field__label">清晰度</span>
                <select v-model="form.quality" :disabled="live">
                  <option value="720">720P</option>
                  <option value="1080">1080P</option>
                  <option value="1440">1440P</option>
                </select>
              </label>
            </div>
            <label class="field">
              <span class="field__label">封面色卡</span>
              <div class="cover-picker">
                <button v-for="(c, i) in COVER_COLORS" :key="i"
                  class="cover-item" :class="{ 'is-on': form.coverIdx === i }"
                  :style="{ background: c }" @click="form.coverIdx = i" type="button" />
              </div>
            </label>
            <label class="field field--inline">
              <input v-model="form.openBarrage" type="checkbox" :disabled="live" />
              <span>开启弹幕（关闭后观众只能看不能聊）</span>
            </label>
            <label class="field field--inline">
              <input v-model="form.openGift" type="checkbox" :disabled="live" />
              <span>开启礼物（关闭后无法打赏）</span>
            </label>
          </form>
        </div>
      </section>

      <!-- 右：实时数据 + 互动 -->
      <aside class="col col--right">
        <!-- 实时指标 -->
        <div class="panel glass">
          <div class="panel__head">
            <h3>实时数据</h3>
            <span class="panel__hint">每 2s 刷新</span>
          </div>
          <div class="metrics">
            <div class="metric">
              <span class="metric__num"><NumberFlip :value="stats.online" /></span>
              <span class="metric__label">当前在线</span>
            </div>
            <div class="metric">
              <span class="metric__num"><NumberFlip :value="stats.peak" /></span>
              <span class="metric__label">峰值在线</span>
            </div>
            <div class="metric">
              <span class="metric__num"><NumberFlip :value="stats.totalView" /></span>
              <span class="metric__label">累计观看</span>
            </div>
            <div class="metric">
              <span class="metric__num"><NumberFlip :value="stats.likes" /></span>
              <span class="metric__label">点赞</span>
            </div>
            <div class="metric">
              <span class="metric__num"><NumberFlip :value="stats.barrage" /></span>
              <span class="metric__label">弹幕</span>
            </div>
            <div class="metric">
              <span class="metric__num"><NumberFlip :value="stats.gift" /></span>
              <span class="metric__label">礼物</span>
            </div>
          </div>
          <div class="revenue">
            <span>本场收益</span>
            <span class="revenue__num">¥{{ stats.revenue.toFixed(2) }}</span>
          </div>
        </div>

        <!-- 互动管理 -->
        <div class="panel glass">
          <div class="panel__head">
            <h3>互动管理</h3>
          </div>
          <div class="actions">
            <button class="action" :class="{ 'is-on': pinTop }" @click="pinTop = !pinTop">
              <span class="action__icon">📌</span>
              <span>{{ pinTop ? '取消置顶' : '置顶公告' }}</span>
            </button>
            <button class="action" @click="onMuteAll">
              <span class="action__icon">🤐</span>
              <span>全场禁言</span>
            </button>
            <button class="action" @click="onKickUser">
              <span class="action__icon">⛔</span>
              <span>踢人</span>
            </button>
            <button class="action" @click="onEndActivity">
              <span class="action__icon">⚡</span>
              <span>结束本场秒杀</span>
            </button>
          </div>
          <label v-if="pinTop" class="field pin-field">
            <textarea v-model.trim="pinText" maxlength="80" placeholder="置顶内容（80 字内）" rows="2" />
          </label>
        </div>

        <!-- 推流信息 -->
        <div class="panel glass">
          <div class="panel__head">
            <h3>推流信息</h3>
          </div>
          <dl class="info">
            <div><dt>推流地址</dt><dd class="num">{{ live ? `rtmp://push.livemall.cn/live/${roomId}` : '-' }}</dd></div>
            <div><dt>推流密钥</dt><dd class="num">{{ live ? '••••••••' + streamKey.slice(-4) : '-' }}</dd></div>
            <div><dt>视频码率</dt><dd>{{ live ? bitRateLabel : '-' }}</dd></div>
            <div><dt>编码</dt><dd>H.264 / AAC</dd></div>
          </dl>
        </div>
      </aside>
    </div>

    <!-- 分享弹窗 -->
    <transition name="modal">
      <div v-if="shareOpen" class="share-mask" @click.self="shareOpen = false">
        <div class="share glass">
          <h3>分享直播间</h3>
          <p class="share__link">{{ shareUrl }}</p>
          <div class="share__actions">
            <NeonButton variant="ghost" @click="copyLink">复制链接</NeonButton>
            <NeonButton @click="shareOpen = false">关闭</NeonButton>
          </div>
        </div>
      </div>
    </transition>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { showToast } from '@/utils/toast'
import { liveApi } from '@/api/live'
import NeonButton from '@/components/base/NeonButton.vue'
import NumberFlip from '@/components/base/NumberFlip.vue'

const COVER_COLORS = [
  'linear-gradient(135deg,#1a1240,#0a2233)',
  'linear-gradient(135deg,#3a0d2a,#2a1a0d)',
  'linear-gradient(135deg,#0d3a2a,#1a2a0d)',
  'linear-gradient(135deg,#2a0d3a,#0d1a3a)',
  'linear-gradient(135deg,#6b4dff,#00e5ff)',
]

const live = ref(false)
const streamPhase = computed(() => live.value ? 'live' : 'idle')
const statusLabel = computed(() => live.value ? '直播中' : '未开播')

const form = reactive({
  title: '',
  category: 'digital',
  quality: '1080',
  coverIdx: 0,
  openBarrage: true,
  openGift: true,
})

// —— 视频流 ——
const videoRef = ref(null)
const stream = ref(null)
const micOn = ref(true)
const camOn = ref(true)

async function onStart() {
  if (!form.title) { showToast('请填写直播标题', 'warning'); return }
  // 1. 先调后端创建房间
  let room
  try {
    room = await liveApi.startRoom({
      title: form.title,
      category: form.category,
      coverColor: COVER_COLORS[form.coverIdx],
    })
  } catch (e) {
    showToast('开播失败：' + (e?.message || '后端未就绪'), 'danger')
    return
  }
  roomId.value = room.id
  // 生成推流密钥（演示用）
  streamKey.value = `${Date.now().toString(36)}${Math.random().toString(36).slice(2, 8)}`
  // 2. 尝试打开摄像头
  try {
    const s = await navigator.mediaDevices.getUserMedia({
      video: camOn.value ? { width: 1280, height: 720 } : false,
      audio: micOn.value,
    })
    stream.value = s
    requestAnimationFrame(() => {
      if (videoRef.value) videoRef.value.srcObject = s
    })
  } catch (e) {
    showToast('未取得摄像头权限，进入无视频演示模式', 'info')
  }
  live.value = true
  startedAt.value = Date.now()
  showToast('直播已开始', 'success')
  startStatsTimer()
}

function onStop() {
  if (!confirm('确定结束本次直播？')) return
  if (roomId.value) {
    liveApi.stopRoom(roomId.value).catch(() => {})
  }
  if (stream.value) {
    stream.value.getTracks().forEach(t => t.stop())
    stream.value = null
  }
  live.value = false
  stopStatsTimer()
  showToast(`本场直播结束 · 时长 ${elapsedLabel.value} · 收益 ¥${stats.revenue.toFixed(2)}`, 'success')
  Object.assign(stats, { online: 0, peak: 0, barrage: 0, gift: 0, likes: 0, revenue: 0 })
}

function onToggleMic() {
  micOn.value = !micOn.value
  if (stream.value) {
    stream.value.getAudioTracks().forEach(t => t.enabled = micOn.value)
  }
  showToast(micOn.value ? '麦克风已开' : '已静音', 'info')
}
function onToggleCam() {
  camOn.value = !camOn.value
  if (stream.value) {
    stream.value.getVideoTracks().forEach(t => t.enabled = camOn.value)
  }
  showToast(camOn.value ? '摄像头已开' : '摄像头已关', 'info')
}

// —— 推流信息 ——
const roomId = ref('')
const streamKey = ref('')
const startedAt = ref(0)
const bitRateLabel = computed(() => {
  const map = { 720: '2.5 Mbps', 1080: '4.5 Mbps', 1440: '8 Mbps' }
  return map[form.quality] || '4.5 Mbps'
})
const elapsedLabel = computed(() => {
  if (!startedAt.value) return '00:00:00'
  const s = Math.floor((Date.now() - startedAt.value) / 1000)
  const pad = n => String(n).padStart(2, '0')
  return `${pad(Math.floor(s / 3600))}:${pad(Math.floor((s % 3600) / 60))}:${pad(s % 60)}`
})

// —— 实时统计（mock） ——
const stats = reactive({
  online: 0, peak: 0, totalView: 0, likes: 0, barrage: 0, gift: 0, revenue: 0,
})
let statsTimer = null
function startStatsTimer() {
  stats.online = 12
  stats.totalView = 12
  stats.peak = 12
  statsTimer = setInterval(() => {
    // 在线人数波动（5s 更新一次，2s 太频繁）
    const delta = Math.floor((Math.random() - 0.45) * 8)
    stats.online = Math.max(1, stats.online + delta)
    stats.peak = Math.max(stats.peak, stats.online)
    stats.totalView += Math.floor(Math.random() * 5)
    stats.likes += Math.floor(Math.random() * 12)
    stats.barrage += Math.floor(Math.random() * 4)
    if (Math.random() < 0.4) {
      const g = [9, 9, 9, 120, 666][Math.floor(Math.random() * 5)]
      stats.gift += 1
      stats.revenue += g
    }
  }, 5000)
}
function stopStatsTimer() {
  if (statsTimer) clearInterval(statsTimer)
  statsTimer = null
}

// —— 互动管理 ——
const pinTop = ref(false)
const pinText = ref('')
function onMuteAll() {
  if (!live.value) { showToast('未在直播中', 'warning'); return }
  showToast('已全场禁言 60 秒', 'warning')
}
function onKickUser() {
  const name = prompt('输入要踢出的用户名 / ID：')
  if (name) showToast(`已踢出 ${name}`, 'warning')
}
function onEndActivity() {
  if (!confirm('确定强制结束当前秒杀活动？')) return
  showToast('秒杀活动已结束', 'info')
}

// —— 分享 ——
const shareOpen = ref(false)
const shareUrl = computed(() => roomId.value ? `${location.origin}/live/${roomId.value}` : '')
function onShare() {
  if (!live.value) { showToast('开播后才能分享', 'warning'); return }
  shareOpen.value = true
}
async function copyLink() {
  try {
    await navigator.clipboard.writeText(shareUrl.value)
    showToast('已复制到剪贴板', 'success')
  } catch {
    showToast('复制失败，请手动选择', 'danger')
  }
}

// —— 刷新后恢复直播状态 ——
async function restoreIfLive() {
  try {
    const room = await liveApi.getMyActive()
    if (room && room.status === 1) {
      roomId.value = room.id
      live.value = true
      startedAt.value = room.startedAt ? new Date(room.startedAt).getTime() : Date.now()
      form.title = room.title
      form.category = room.category
      startStatsTimer()
      // 尝试重连摄像头
      try {
        const s = await navigator.mediaDevices.getUserMedia({
          video: camOn.value ? { width: 1280, height: 720 } : false,
          audio: micOn.value,
        })
        stream.value = s
        requestAnimationFrame(() => {
          if (videoRef.value) videoRef.value.srcObject = s
        })
      } catch { /* 摄像头不可用，保持无视频模式 */ }
      showToast('已恢复直播', 'info')
    }
  } catch { /* 后端未就绪 */ }
}

onMounted(() => { restoreIfLive() })

onBeforeUnmount(() => {
  stopStatsTimer()
  if (stream.value) stream.value.getTracks().forEach(t => t.stop())
})
</script>

<style scoped>
.console { display: flex; flex-direction: column; gap: 16px; }

/* —— 顶部状态条 —— */
.console__head {
  display: flex; align-items: center; justify-content: space-between;
  padding: 14px 20px;
  border-radius: var(--radius);
  border: 1px solid var(--border-soft);
  background: var(--bg-card);
}
.head__left { display: flex; align-items: center; gap: 14px; }
.status-dot {
  width: 10px; height: 10px;
  border-radius: 50%;
}
.status-dot--live { background: var(--danger); box-shadow: 0 0 12px var(--danger); animation: pulse 1.2s infinite; }
.status-dot--idle { background: var(--text-dim); }
@keyframes pulse { 50% { opacity: 0.4; } }
.status-text {
  font-family: var(--font-display);
  font-size: 15px;
  font-weight: 600;
  color: var(--text-strong);
  letter-spacing: 0.06em;
}
.status-time { font-size: 13px; color: var(--text-muted); font-family: var(--font-num); }
.head__right { display: flex; align-items: center; gap: 20px; }
.head__metric { display: flex; flex-direction: column; align-items: flex-end; }
.head__metric-label { font-size: 11px; color: var(--text-dim); }
.head__metric .num { font-family: var(--font-num); font-size: 18px; color: var(--neon-cyan); font-weight: 700; }

/* —— 主体 —— */
.console__body { display: grid; grid-template-columns: 1fr 340px; gap: 16px; }
@media (max-width: 1100px) { .console__body { grid-template-columns: 1fr; } }
.col { display: flex; flex-direction: column; gap: 16px; }

/* —— 预览 —— */
.preview {
  position: relative;
  aspect-ratio: 16 / 9;
  border-radius: var(--radius-lg);
  overflow: hidden;
  border: 1px solid var(--border-soft);
  background: #050617;
}
.preview.is-live { border-color: var(--danger); box-shadow: 0 0 0 1px var(--danger) inset, 0 0 24px rgba(255,84,112,0.2); }
.preview video { width: 100%; height: 100%; object-fit: cover; background: #050617; }
.preview__placeholder { position: absolute; inset: 0; display: grid; place-items: center; }
.preview__placeholder-bg {
  position: absolute; inset: 0;
  background:
    radial-gradient(circle at 30% 40%, rgba(138,99,255,0.18), transparent 50%),
    radial-gradient(circle at 70% 60%, rgba(0,229,255,0.14), transparent 50%);
}
.preview__placeholder-content { position: relative; text-align: center; color: var(--text-muted); }
.preview__icon { font-size: 48px; margin-bottom: 8px; }
.preview__btn {
  margin-top: 12px;
  padding: 8px 20px;
  border-radius: 999px;
  background: linear-gradient(135deg, var(--neon-purple), var(--neon-cyan));
  color: #07081a;
  border: none;
  font-weight: 600;
  cursor: pointer;
}
.preview__badges {
  position: absolute; top: 12px; left: 12px;
  display: flex; gap: 6px;
}
.preview__badge {
  padding: 4px 10px;
  border-radius: 4px;
  background: rgba(7,8,26,0.7);
  backdrop-filter: blur(4px);
  font-size: 11px;
  color: var(--text);
  font-family: var(--font-num);
}
.preview__badge--live { background: var(--danger); color: #fff; font-weight: 700; }
.preview__badge--live .dot {
  display: inline-block;
  width: 6px; height: 6px;
  background: #fff;
  border-radius: 50%;
  margin-right: 4px;
  animation: pulse 1s infinite;
}
.preview__float {
  position: absolute; top: 12px; right: 12px;
  display: flex; flex-direction: column; gap: 6px;
}
.float__item {
  width: 48px; height: 48px;
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  gap: 2px;
  border-radius: 10px;
  background: rgba(7,8,26,0.7);
  backdrop-filter: blur(6px);
  cursor: pointer;
  transition: background 0.2s, transform 0.15s;
}
.float__item:hover { background: rgba(138,99,255,0.4); transform: scale(1.05); }
.float__icon { font-size: 16px; }
.float__label { font-size: 10px; color: var(--text); }

/* —— 面板 —— */
.panel {
  border-radius: var(--radius);
  padding: 18px 20px;
  border: 1px solid var(--border-faint);
  background: var(--bg-card);
}
.panel__head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 14px; }
.panel__head h3 { margin: 0; font-family: var(--font-display); font-size: 15px; color: var(--text-strong); letter-spacing: 0.06em; }
.panel__hint { font-size: 11px; color: var(--text-dim); }

/* —— 表单 —— */
.form { display: flex; flex-direction: column; gap: 12px; }
.field { display: flex; flex-direction: column; gap: 6px; }
.field--inline { flex-direction: row; align-items: center; gap: 8px; font-size: 13px; color: var(--text); }
.field__label { font-size: 12px; color: var(--text-muted); letter-spacing: 0.05em; }
.field-row { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
.field input[type="text"],
.field input[type="url"],
.field select,
.field textarea {
  height: 36px;
  padding: 0 12px;
  background: rgba(7,8,26,0.6);
  border: 1px solid var(--border-faint);
  border-radius: var(--radius);
  color: var(--text);
  font-size: 13px;
  font-family: var(--font-body);
  transition: border-color 0.2s, box-shadow 0.2s;
}
.field textarea { height: auto; padding: 8px 12px; resize: vertical; }
.field input:focus, .field select:focus, .field textarea:focus {
  outline: none;
  border-color: var(--neon-purple);
  box-shadow: 0 0 0 3px var(--neon-purple-soft);
}
.field input:disabled, .field select:disabled { opacity: 0.5; cursor: not-allowed; }
.cover-picker { display: flex; gap: 8px; flex-wrap: wrap; }
.cover-item {
  width: 48px; height: 28px;
  border-radius: 6px;
  border: 2px solid transparent;
  cursor: pointer;
  transition: transform 0.15s, border-color 0.15s;
}
.cover-item:hover { transform: scale(1.06); }
.cover-item.is-on { border-color: var(--neon-cyan); box-shadow: 0 0 8px var(--neon-cyan-soft); }

/* —— 指标 —— */
.metrics {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
  margin-bottom: 12px;
}
.metric {
  padding: 12px 8px;
  display: flex; flex-direction: column; align-items: center; gap: 4px;
  border-radius: var(--radius);
  background: rgba(7,8,26,0.4);
  border: 1px solid var(--border-faint);
}
.metric__num {
  font-family: var(--font-num);
  font-size: 18px;
  font-weight: 700;
  color: var(--text-strong);
}
.metric__label { font-size: 11px; color: var(--text-muted); }
.revenue {
  display: flex; align-items: center; justify-content: space-between;
  padding: 12px 14px;
  border-radius: var(--radius);
  background: linear-gradient(90deg, rgba(255,77,79,0.08), rgba(255,138,0,0.06));
  border: 1px solid rgba(255,77,79,0.25);
}
.revenue__num {
  font-family: var(--font-num);
  font-size: 20px;
  font-weight: 700;
  color: var(--warning);
  text-shadow: 0 0 12px rgba(255,138,0,0.4);
}

/* —— 互动管理 —— */
.actions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}
.action {
  padding: 12px 8px;
  display: flex; flex-direction: column; align-items: center; gap: 4px;
  border-radius: var(--radius);
  border: 1px solid var(--border-faint);
  background: rgba(7,8,26,0.4);
  color: var(--text-muted);
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s;
}
.action:hover { border-color: var(--neon-purple); color: var(--text-strong); }
.action.is-on { border-color: var(--neon-cyan); color: var(--neon-cyan); background: rgba(0,229,255,0.08); }
.action__icon { font-size: 20px; }
.pin-field { margin-top: 10px; }

/* —— 推流信息 —— */
.info { margin: 0; display: flex; flex-direction: column; gap: 8px; }
.info > div {
  display: flex; justify-content: space-between; align-items: center;
  font-size: 12px;
  padding: 6px 0;
  border-bottom: 1px dashed var(--border-faint);
}
.info > div:last-child { border-bottom: none; }
.info dt { color: var(--text-muted); }
.info dd { margin: 0; color: var(--text); }
.info .num { font-family: var(--font-num); }

/* —— 分享弹窗 —— */
.share-mask {
  position: fixed; inset: 0; z-index: var(--z-modal);
  background: rgba(7,8,26,0.7);
  backdrop-filter: blur(6px);
  display: grid; place-items: center;
}
.share {
  width: 400px;
  padding: 24px;
  border-radius: var(--radius-lg);
  border: 1px solid var(--border-soft);
  display: flex; flex-direction: column; gap: 14px;
}
.share h3 { margin: 0; font-family: var(--font-display); color: var(--text-strong); }
.share__link {
  padding: 10px 12px;
  background: rgba(7,8,26,0.6);
  border: 1px solid var(--border-faint);
  border-radius: var(--radius);
  font-family: var(--font-num);
  font-size: 12px;
  color: var(--neon-cyan);
  word-break: break-all;
  margin: 0;
}
.share__actions { display: flex; gap: 10px; justify-content: flex-end; }

.modal-enter-active, .modal-leave-active { transition: opacity 0.2s; }
.modal-enter-from, .modal-leave-to { opacity: 0; }
</style>
