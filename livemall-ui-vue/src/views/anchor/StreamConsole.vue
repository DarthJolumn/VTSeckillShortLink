<template>
  <div class="console" :class="{ 'is-live': live, 'is-vertical': layout === 'vertical' }">
    <!-- ====== 顶部状态条 ====== -->
    <header class="console__bar">
      <div class="bar__left">
        <span class="status-dot" :class="live ? 'status-dot--live' : 'status-dot--idle'" />
        <span class="bar__status-text">{{ live ? '直播中' : '未开播' }}</span>
        <span v-if="live" class="bar__elapsed num">{{ elapsedLabel }}</span>
      </div>
      <div class="bar__center">
        <span class="bar__metric"><span class="bar__metric-label">在线</span><NumberFlip :value="roomOnline" /></span>
        <span class="bar__metric"><span class="bar__metric-label">观看</span><NumberFlip :value="stats.totalView" /></span>
        <span class="bar__metric"><span class="bar__metric-label">收益</span><span class="num">¥{{ stats.revenue.toFixed(0) }}</span></span>
      </div>
      <div class="bar__right">
        <button class="bar__layout-btn" @click="layout = layout === 'default' ? 'vertical' : 'default'" title="切换布局">
          {{ layout === 'default' ? '⬛' : '⬜' }}
        </button>
        <NeonButton v-if="!live" variant="seckill" @click="onStart">开始直播</NeonButton>
        <NeonButton v-else variant="ghost" @click="onStop">结束直播</NeonButton>
      </div>
    </header>

    <!-- ====== 主体 ====== -->
    <div class="console__body">
      <!-- 左：视频 + 秒杀卡 -->
      <section class="col col--main">
        <!-- 视频区 -->
        <div class="player" :class="{ 'is-live': live }">
          <video v-if="stream" ref="videoRef" autoplay muted playsinline class="player__video" />
          <div v-else class="player__placeholder">
            <div class="player__placeholder-glow" />
            <span class="player__placeholder-icon">{{ live ? '📡' : '🎥' }}</span>
            <p>{{ live ? '推流中…' : '点击「开始直播」' }}</p>
          </div>

          <!-- 弹幕浮层（可关闭） -->
          <BarrageTrack
            v-if="showBarrage"
            class="player__barrage"
            :messages="comments"
            :lanes="4"
          />

          <!-- 左上角标 -->
          <div v-if="live" class="player__badges">
            <span class="player__badge player__badge--live"><i class="dot" /> LIVE</span>
            <span class="player__badge" @click="showBarrage = !showBarrage" style="cursor:pointer">
              {{ showBarrage ? '💬 弹幕开' : '💬 弹幕关' }}
            </span>
          </div>

          <!-- 右下控制浮层 -->
          <div v-if="live" class="player__ctrls">
            <button class="ctrl-btn" :class="{ 'is-off': !micOn }" @click="onToggleMic" :title="micOn ? '麦克风开' : '已静音'">
              {{ micOn ? '🎙️' : '🔇' }}
            </button>
            <button class="ctrl-btn" :class="{ 'is-off': !camOn }" @click="onToggleCam" :title="camOn ? '摄像头开' : '已关闭'">
              {{ camOn ? '📹' : '🚫' }}
            </button>
            <button class="ctrl-btn" @click="onShare" title="分享">🔗</button>
          </div>
        </div>

        <!-- 秒杀管理卡 -->
        <div class="seckill-panel glass">
          <div class="sp-head">
            <h3>⚡ 秒杀管理</h3>
            <div class="sp-head__actions">
              <NeonButton v-if="!hasActivity" variant="purple" size="sm" @click="seckillDlgOpen = true">创建活动</NeonButton>
              <template v-else>
                <span class="sp-status-tag" :class="`sp-status-tag--${activityPhase}`">{{ activityStatusLabel }}</span>
                <NeonButton v-if="activity.status === 0" variant="seckill" size="sm" @click="onActivityGoLive">上架</NeonButton>
                <NeonButton v-if="activity.status === 1" variant="ghost" size="sm" @click="onActivityEnd">下架</NeonButton>
                <NeonButton variant="ghost" size="sm" @click="seckillDlgOpen = true">编辑</NeonButton>
              </template>
            </div>
          </div>
          <template v-if="hasActivity">
            <div class="sp-product">
              <div class="sp-product__thumb" :style="{ background: activity.cover || COVER_COLORS[0] }" />
              <div class="sp-product__info">
                <span class="sp-product__name">{{ activity.name }}</span>
                <div class="sp-product__price">
                  <span class="num">¥{{ activity.price }}</span>
                  <span class="sp-product__orig">¥{{ activity.origPrice }}</span>
                  <span class="sp-product__discount">{{ discount }}折</span>
                </div>
              </div>
            </div>
            <StockBar :stock="activity.stockSold != null ? (activity.stockTotal - activity.stockSold) : 0" :total="activity.stockTotal || 1" />
            <div class="sp-meta">
              <span>已售 {{ activity.stockSold || 0 }}/{{ activity.stockTotal || 0 }}</span>
              <span>限购 {{ activity.limit || 1 }} 件/人</span>
            </div>
          </template>
          <div v-else class="sp-empty">
            <span>暂无秒杀活动，点击「创建活动」添加</span>
          </div>
        </div>
      </section>

      <!-- 右：评论区 + 数据 -->
      <aside class="col col--side">
        <!-- 评论区（竖排滚动，类似抖音） -->
        <div class="comment-panel glass">
          <div class="cp-head">
            <h3>💬 实时评论</h3>
            <span class="cp-count num">{{ comments.length }}</span>
          </div>
          <div class="cp-list no-scrollbar" ref="commentListRef">
            <TransitionGroup name="comment">
              <div v-for="c in comments.slice(-60)" :key="c.timestamp + '-' + (c.userId || c.username)"
                class="cp-item" :class="{ 'is-gift': c.giftName, 'is-self': c.self }">
                <span class="cp-item__user" :style="{ color: pickColor(c.userId || 0) }">{{ c.username || c.nickname || '匿名' }}</span>
                <template v-if="c.giftName">
                  <span class="cp-item__gift">送出 {{ c.giftName }} ×{{ c.quantity }} {{ c.giftIcon }}</span>
                </template>
                <span v-else class="cp-item__text">{{ c.content }}</span>
              </div>
            </TransitionGroup>
          </div>
          <!-- 快捷输入 -->
          <form class="cp-input" @submit.prevent="onSendComment">
            <input v-model="commentDraft" type="text" placeholder="和观众互动…" maxlength="40" />
            <button type="submit" :disabled="!commentDraft.trim()">发送</button>
          </form>
        </div>

        <!-- 实时数据 + 贡献榜 -->
        <div class="side-bottom">
          <div class="stats-panel glass">
            <div class="stats-grid">
              <div class="stat"><span class="stat__val num"><NumberFlip :value="roomOnline" /></span><span class="stat__lbl">在线</span></div>
              <div class="stat"><span class="stat__val num"><NumberFlip :value="stats.peak" /></span><span class="stat__lbl">峰值</span></div>
              <div class="stat"><span class="stat__val num"><NumberFlip :value="stats.likes" /></span><span class="stat__lbl">点赞</span></div>
              <div class="stat"><span class="stat__val num"><NumberFlip :value="stats.barrage" /></span><span class="stat__lbl">弹幕</span></div>
              <div class="stat"><span class="stat__val num"><NumberFlip :value="stats.gift" /></span><span class="stat__lbl">礼物</span></div>
              <div class="stat stat--revenue"><span class="stat__val num">¥{{ stats.revenue.toFixed(0) }}</span><span class="stat__lbl">收益</span></div>
            </div>
          </div>

          <!-- 贡献榜 -->
          <div class="rank-panel glass" v-if="leaderboard.length">
            <div class="rp-head"><span>🏆 贡献榜</span></div>
            <div class="rp-list no-scrollbar">
              <div v-for="(u, i) in leaderboard.slice(0, 8)" :key="u.id" class="rp-row">
                <span class="rp-row__rank" :class="{ 'is-top': i < 3 }">{{ i + 1 }}</span>
                <span class="rp-row__name">{{ u.name || u.username }}</span>
                <span class="rp-row__score num">{{ u.score || 0 }}</span>
              </div>
            </div>
          </div>
        </div>
      </aside>
    </div>

    <!-- ====== 底部：推流信息 + 设置折叠 ====== -->
    <footer class="console__foot">
      <div class="foot__info">
        <span class="foot__info-item">推流: {{ live ? 'rtmp://push.livemall.cn/live/' + roomId : '—' }}</span>
        <span class="foot__info-item">码率: {{ bitRateLabel }}</span>
        <span class="foot__info-item">编码: H.264/AAC</span>
      </div>
      <button class="foot__toggle" @click="settingsOpen = !settingsOpen">
        {{ settingsOpen ? '收起设置 ▲' : '直播设置 ▼' }}
      </button>
    </footer>

    <!-- 设置面板（折叠） -->
    <transition name="slide">
      <div v-if="settingsOpen" class="settings glass">
        <div class="settings__grid">
          <label class="s-field">
            <span>直播标题</span>
            <input v-model.trim="form.title" type="text" maxlength="40" :disabled="live" placeholder="深夜数码秒杀局" />
          </label>
          <label class="s-field">
            <span>分类</span>
            <select v-model="form.category" :disabled="live">
              <option value="digital">数码</option><option value="beauty">美妆</option>
              <option value="food">食品</option><option value="fashion">服饰</option><option value="other">其他</option>
            </select>
          </label>
          <label class="s-field">
            <span>清晰度</span>
            <select v-model="form.quality" :disabled="live">
              <option value="720">720P</option><option value="1080">1080P</option><option value="1440">1440P</option>
            </select>
          </label>
          <div class="s-field">
            <span>封面色</span>
            <div class="cover-row">
              <button v-for="(c, i) in COVER_COLORS" :key="i" class="cover-dot" :class="{ on: form.coverIdx === i }"
                :style="{ background: c }" @click="form.coverIdx = i" type="button" />
            </div>
          </div>
        </div>
        <div class="settings__toggles">
          <label class="toggle"><input v-model="showBarrage" type="checkbox" /><span>视频弹幕浮层</span></label>
          <label class="toggle"><input v-model="form.openGift" type="checkbox" :disabled="live" /><span>开启礼物</span></label>
        </div>
      </div>
    </transition>

    <!-- ====== 秒杀活动弹窗 ====== -->
    <transition name="modal">
      <div v-if="seckillDlgOpen" class="dlg-mask" @click.self="seckillDlgOpen = false">
        <div class="dlg glass">
          <div class="dlg__head">
            <h3>{{ editingActivity ? '编辑秒杀活动' : '创建秒杀活动' }}</h3>
            <button class="dlg__close" @click="seckillDlgOpen = false">✕</button>
          </div>
          <form class="dlg__form" @submit.prevent="onSaveActivity">
            <label class="dlg-field"><span>商品名称</span><input v-model.trim="seckillForm.name" type="text" maxlength="60" placeholder="旗舰降噪耳机 · 限量500台" required /></label>
            <div class="dlg-row">
              <label class="dlg-field"><span>秒杀价 ¥</span><input v-model.number="seckillForm.price" type="number" min="1" required /></label>
              <label class="dlg-field"><span>原价 ¥</span><input v-model.number="seckillForm.origPrice" type="number" min="1" required /></label>
            </div>
            <div class="dlg-row">
              <label class="dlg-field"><span>总库存</span><input v-model.number="seckillForm.stockTotal" type="number" min="1" required /></label>
              <label class="dlg-field"><span>限购数</span><input v-model.number="seckillForm.limit" type="number" min="1" /></label>
            </div>
            <div class="dlg-row">
              <label class="dlg-field"><span>开始时间</span><input v-model="seckillForm.startAt" type="datetime-local" required /></label>
              <label class="dlg-field"><span>持续(分钟)</span><input v-model.number="seckillForm.durationMin" type="number" min="1" max="480" /></label>
            </div>
            <div class="dlg-field"><span>封面色</span>
              <div class="cover-row">
                <button v-for="(c, i) in COVER_COLORS" :key="i" class="cover-dot" :class="{ on: seckillForm.coverIdx === i }"
                  :style="{ background: c }" @click="seckillForm.coverIdx = i" type="button" />
              </div>
            </div>
            <div class="dlg__actions">
              <NeonButton variant="ghost" type="button" @click="seckillDlgOpen = false">取消</NeonButton>
              <NeonButton variant="seckill" type="submit" :loading="savingActivity">{{ editingActivity ? '保存' : '创建' }}</NeonButton>
            </div>
          </form>
        </div>
      </div>
    </transition>

    <!-- ====== 分享弹窗 ====== -->
    <transition name="modal">
      <div v-if="shareOpen" class="dlg-mask" @click.self="shareOpen = false">
        <div class="dlg glass" style="max-width:400px">
          <h3 style="margin-top:0">分享直播间</h3>
          <p class="share-url">{{ shareUrl }}</p>
          <div style="display:flex;gap:10px;justify-content:flex-end">
            <NeonButton variant="ghost" @click="copyLink">复制链接</NeonButton>
            <NeonButton @click="shareOpen = false">关闭</NeonButton>
          </div>
        </div>
      </div>
    </transition>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { showToast } from '@/utils/toast'
import { liveApi } from '@/api/live'
import { seckillApi } from '@/api/seckill'
import { useLiveStore } from '@/stores/live'
import NeonButton from '@/components/base/NeonButton.vue'
import NumberFlip from '@/components/base/NumberFlip.vue'
import BarrageTrack from '@/components/base/BarrageTrack.vue'
import StockBar from '@/components/base/StockBar.vue'

const live = useLiveStore()

const COVER_COLORS = [
  'linear-gradient(135deg,#1a1240,#0a2233)',
  'linear-gradient(135deg,#3a0d2a,#2a1a0d)',
  'linear-gradient(135deg,#0d3a2a,#1a2a0d)',
  'linear-gradient(135deg,#2a0d3a,#0d1a3a)',
  'linear-gradient(135deg,#6b4dff,#00e5ff)',
  'linear-gradient(135deg,#ff5470,#ff8a00)',
]

// —— 布局 ——
const layout = ref('default') // default | vertical

// —— 直播状态 ——
const videoRef = ref(null)
const stream = ref(null)
const micOn = ref(true)
const camOn = ref(true)
const roomId = ref('')
const streamKey = ref('')
const startedAt = ref(0)
const liveFlag = ref(false) // 本地 live 状态（非 store）
const showBarrage = ref(true)   // 弹幕浮层开关
const settingsOpen = ref(false)

const form = reactive({
  title: '', category: 'digital', quality: '1080', coverIdx: 0, openGift: true,
})

const bitRateLabel = computed(() => ({ 720: '2.5 Mbps', 1080: '4.5 Mbps', 1440: '8 Mbps' }[form.quality] || '4.5 Mbps'))
const elapsedLabel = computed(() => {
  if (!startedAt.value) return '00:00:00'
  const s = Math.floor((Date.now() - startedAt.value) / 1000)
  const pad = n => String(n).padStart(2, '0')
  return `${pad(Math.floor(s / 3600))}:${pad(Math.floor((s % 3600) / 60))}:${pad(s % 60)}`
})

// —— 开播 / 关播 ——
async function onStart() {
  if (!form.title) { showToast('请填写直播标题', 'warning'); return }
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
  streamKey.value = `${Date.now().toString(36)}${Math.random().toString(36).slice(2, 8)}`
  try {
    const s = await navigator.mediaDevices.getUserMedia({
      video: camOn.value ? { width: 1280, height: 720 } : false,
      audio: micOn.value,
    })
    stream.value = s
    requestAnimationFrame(() => { if (videoRef.value) videoRef.value.srcObject = s })
  } catch { showToast('未取得摄像头权限，进入无视频演示模式', 'info') }
  liveFlag.value = true
  startedAt.value = Date.now()
  // 连自己的房间 WS 收评论
  live.join(room.id)
  startStatsTimer()
  showToast('直播已开始', 'success')
}

function onStop() {
  if (!confirm('确定结束本次直播？')) return
  if (roomId.value) liveApi.stopRoom(roomId.value).catch(() => {})
  if (stream.value) { stream.value.getTracks().forEach(t => t.stop()); stream.value = null }
  liveFlag.value = false
  live.leave()
  stopStatsTimer()
  showToast(`本场结束 · ${elapsedLabel.value} · 收益 ¥${stats.revenue.toFixed(2)}`, 'success')
  Object.assign(stats, { online: 0, peak: 0, barrage: 0, gift: 0, likes: 0, revenue: 0, totalView: 0 })
}

function onToggleMic() {
  micOn.value = !micOn.value
  if (stream.value) stream.value.getAudioTracks().forEach(t => t.enabled = micOn.value)
}
function onToggleCam() {
  camOn.value = !camOn.value
  if (stream.value) stream.value.getVideoTracks().forEach(t => t.enabled = camOn.value)
}

// —— 评论（来自 WS 的弹幕 + 礼物消息） ——
const comments = ref([])
const commentListRef = ref(null)
const commentDraft = ref('')

// WS 弹幕消息 → 追加到本地 comments（BarrageTrack 浮层 + 右侧评论面板共用）
watch(() => live.barrage.length, () => {
  const latest = live.barrage[live.barrage.length - 1]
  if (latest) {
    comments.value.push(latest)
    // 超过 500 条时裁剪（slice 创建新数组会触发 BarrageTrack 重置，但阈值高影响小）
    if (comments.value.length > 500) comments.value = comments.value.slice(-300)
    nextTick(() => { const el = commentListRef.value; if (el) el.scrollTop = el.scrollHeight })
  }
})

watch(() => live.giftFeed.length, () => {
  const latest = live.giftFeed[0]
  if (latest) {
    comments.value.push({ ...latest, giftName: latest.giftName || '礼物', giftIcon: latest.giftIcon || '🎁' })
    if (comments.value.length > 500) comments.value = comments.value.slice(-300)
    nextTick(() => { const el = commentListRef.value; if (el) el.scrollTop = el.scrollHeight })
  }
})

function onSendComment() {
  if (!commentDraft.value.trim()) return
  if (!live.sendBarrage(commentDraft.value)) {
    showToast('连接未就绪', 'warning')
    return
  }
  // 自己发的也显示
  comments.value.push({ username: '我', content: commentDraft.value.trim(), self: true, timestamp: Date.now(), userId: 0 })
  commentDraft.value = ''
  nextTick(() => {
    const el = commentListRef.value
    if (el) el.scrollTop = el.scrollHeight
  })
}

// —— 实时统计 ——
const stats = reactive({ online: 0, peak: 0, totalView: 0, likes: 0, barrage: 0, gift: 0, revenue: 0 })
const roomOnline = ref(0)
const leaderboard = ref([])
let statsTimer = null

watch(() => live.online, v => { roomOnline.value = v; updateStatsFromLive() })
watch(() => live.leaderboard.length, () => { leaderboard.value = [...live.leaderboard] })

function updateStatsFromLive() {
  const liveData = live.getSnapshot?.()
  if (liveData) {
    roomOnline.value = liveData.online || roomOnline.value
    leaderboard.value = liveData.scores || leaderboard.value
  } else {
    roomOnline.value = live.online || roomOnline.value
  }
}

function startStatsTimer() {
  stats.online = 12; stats.totalView = 12; stats.peak = 12
  statsTimer = setInterval(() => {
    const delta = Math.floor((Math.random() - 0.45) * 8)
    const online = Math.max(1, (roomOnline.value || stats.online) + delta)
    if (roomOnline.value < 1) roomOnline.value = online
    stats.online = online
    stats.peak = Math.max(stats.peak, online)
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
function stopStatsTimer() { if (statsTimer) clearInterval(statsTimer); statsTimer = null }

// —— 秒杀管理 ——
const seckillDlgOpen = ref(false)
const editingActivity = ref(null)
const savingActivity = ref(false)
const activity = reactive({
  id: null, name: '', price: 299, origPrice: 899, stockTotal: 500, stockSold: 0,
  status: null, cover: '', limit: 1,
})
const hasActivity = computed(() => !!activity.id)
const activityPhase = computed(() => activity.status)
const activityStatusLabel = computed(() => ({ 0: '待开始', 1: '进行中', 2: '已结束', 3: '已取消' }[activity.status] || '—'))
const discount = computed(() => activity.price && activity.origPrice ? Math.round((activity.price / activity.origPrice) * 10) : 0)

const seckillForm = reactive({
  name: '', price: 299, origPrice: 899, stockTotal: 500, limit: 1,
  startAt: '', durationMin: 30, coverIdx: 0,
})

function openSeckillDialog(existing) {
  if (existing) {
    editingActivity.value = existing
    Object.assign(seckillForm, {
      name: existing.name || '', price: existing.price || 299, origPrice: existing.origPrice || 899,
      stockTotal: existing.stockTotal || 500, limit: existing.limit || 1,
      startAt: existing.startAt ? new Date(existing.startAt).toISOString().slice(0, 16) : '',
      durationMin: existing.durationMin || 30, coverIdx: existing.coverIdx || 0,
    })
  } else {
    editingActivity.value = null
    const now = new Date(Date.now() + 5 * 60 * 1000)
    Object.assign(seckillForm, {
      name: '', price: 299, origPrice: 899, stockTotal: 500, limit: 1,
      startAt: now.toISOString().slice(0, 16), durationMin: 30, coverIdx: 0,
    })
  }
  seckillDlgOpen.value = true
}

async function onSaveActivity() {
  if (seckillForm.price >= seckillForm.origPrice) { showToast('秒杀价应低于原价', 'warning'); return }
  savingActivity.value = true
  const startAt = new Date(seckillForm.startAt).getTime()
  const payload = {
    name: seckillForm.name,
    price: seckillForm.price,
    origPrice: seckillForm.origPrice,
    stockTotal: seckillForm.stockTotal,
    startAt,
    endAt: startAt + seckillForm.durationMin * 60 * 1000,
    limit: seckillForm.limit,
    cover: COVER_COLORS[seckillForm.coverIdx],
    coverIdx: seckillForm.coverIdx,
    durationMin: seckillForm.durationMin,
    discount: Math.round((seckillForm.price / seckillForm.origPrice) * 10),
  }
  try {
    if (editingActivity.value) {
      // 编辑暂不支持单独 update，用 status 管理
      Object.assign(activity, payload, { id: editingActivity.value.id || activity.id })
      showToast('活动已更新（本地）', 'success')
    } else {
      const created = await seckillApi.createActivity(payload)
      Object.assign(activity, payload, { id: created?.id || `A${Date.now().toString(36).toUpperCase()}` })
      showToast('秒杀活动已创建', 'success')
    }
  } catch (e) {
    // 后端未就绪 → 本地创建
    Object.assign(activity, payload, { id: `A${Date.now().toString(36).toUpperCase()}`, status: 0 })
    showToast('活动已创建（演示）', 'success')
  } finally {
    savingActivity.value = false
    seckillDlgOpen.value = false
  }
}

async function onActivityGoLive() {
  if (!activity.id) return
  try {
    await seckillApi.updateActivityStatus(activity.id, 1)
    activity.status = 1
    showToast('秒杀活动已上架', 'success')
  } catch { activity.status = 1; showToast('已上架（演示）', 'success') }
}
async function onActivityEnd() {
  if (!activity.id) return
  try {
    await seckillApi.updateActivityStatus(activity.id, 2)
    activity.status = 2
    showToast('秒杀活动已下架', 'info')
  } catch { activity.status = 2; showToast('已下架（演示）', 'info') }
}

// —— 分享 ——
const shareOpen = ref(false)
const shareUrl = computed(() => roomId.value ? `${location.origin}/live/${roomId.value}` : '')
function onShare() {
  if (!liveFlag.value) { showToast('开播后才能分享', 'warning'); return }
  shareOpen.value = true
}
async function copyLink() {
  try { await navigator.clipboard.writeText(shareUrl.value); showToast('已复制', 'success') }
  catch { showToast('复制失败，请手动选择', 'danger') }
}

// —— 恢复直播 ——
async function restoreIfLive() {
  try {
    const room = await liveApi.getMyActive()
    if (room && room.status === 1) {
      roomId.value = room.id
      liveFlag.value = true
      startedAt.value = room.startedAt ? new Date(room.startedAt).getTime() : Date.now()
      form.title = room.title || ''
      form.category = room.category || 'digital'
      live.join(room.id)
      startStatsTimer()
      try {
        const s = await navigator.mediaDevices.getUserMedia({
          video: camOn.value ? { width: 1280, height: 720 } : false, audio: micOn.value,
        })
        stream.value = s
        requestAnimationFrame(() => { if (videoRef.value) videoRef.value.srcObject = s })
      } catch { /* 摄像头不可用 */ }
      showToast('已恢复直播', 'info')
    }
  } catch { /* 后端未就绪 */ }
}

onMounted(() => { restoreIfLive() })
onBeforeUnmount(() => {
  stopStatsTimer()
  if (stream.value) stream.value.getTracks().forEach(t => t.stop())
  live.leave()
})

// —— 工具 ——
const COLORS = ['#8a63ff', '#00e5ff', '#ff7ad9', '#52e5a4', '#ffcb55', '#4cc9f0']
function pickColor(id) { return COLORS[Math.abs(id) % COLORS.length] }
</script>

<style scoped>
.console { display: flex; flex-direction: column; height: calc(100vh - 60px); min-height: 700px; gap: 10px; }

/* ====== 顶栏 ====== */
.console__bar {
  display: flex; align-items: center; justify-content: space-between;
  padding: 10px 20px;
  border-radius: var(--radius);
  border: 1px solid var(--border-faint);
  background: rgba(7,8,26,0.5);
  backdrop-filter: blur(10px);
  flex-shrink: 0;
}
.bar__left { display: flex; align-items: center; gap: 10px; }
.status-dot { width: 10px; height: 10px; border-radius: 50%; }
.status-dot--live { background: var(--danger); box-shadow: 0 0 10px var(--danger); animation: pulse 1.2s infinite; }
.status-dot--idle { background: var(--text-dim); }
@keyframes pulse { 50% { opacity: 0.4; } }
.bar__status-text { font-family: var(--font-display); font-size: 15px; font-weight: 600; color: var(--text-strong); letter-spacing: 0.06em; }
.bar__elapsed { font-size: 13px; color: var(--text-muted); }
.bar__center { display: flex; gap: 20px; }
.bar__metric { display: flex; flex-direction: column; align-items: center; gap: 1px; }
.bar__metric-label { font-size: 10px; color: var(--text-dim); text-transform: uppercase; letter-spacing: 0.1em; }
.bar__metric .num { font-family: var(--font-num); font-size: 17px; color: var(--neon-cyan); font-weight: 700; }
.bar__right { display: flex; align-items: center; gap: 10px; }
.bar__layout-btn { width: 30px; height: 30px; border-radius: 6px; border: 1px solid var(--border-faint); background: transparent; cursor: pointer; font-size: 14px; }

/* ====== 主体 ====== */
.console__body { flex: 1; display: grid; grid-template-columns: minmax(0, 1.4fr) 380px; gap: 12px; min-height: 0; }
.col { display: flex; flex-direction: column; gap: 10px; min-height: 0; }
.col--main { }
.col--side { display: flex; flex-direction: column; gap: 10px; min-height: 0; }

/* ====== 视频播放器 ====== */
.player {
  position: relative;
  aspect-ratio: 16/9;
  border-radius: var(--radius-lg);
  overflow: hidden;
  border: 1px solid var(--border-soft);
  background: #050617;
  flex-shrink: 0;
}
.player.is-live { border-color: var(--danger); box-shadow: 0 0 0 1px rgba(255,77,79,0.5) inset, 0 0 24px rgba(255,84,112,0.2); }
.player__video { width: 100%; height: 100%; object-fit: cover; background: #050617; }
.player__placeholder { position: absolute; inset: 0; display: grid; place-items: center; text-align: center; color: var(--text-muted); }
.player__placeholder-glow {
  position: absolute; inset: 0;
  background: radial-gradient(circle at 30% 40%, rgba(138,99,255,0.15), transparent 50%),
              radial-gradient(circle at 70% 60%, rgba(0,229,255,0.10), transparent 50%);
}
.player__placeholder-icon { font-size: 42px; position: relative; }
.player__placeholder p { font-size: 13px; position: relative; margin-top: 4px; }
.player__barrage { position: absolute; inset: 0; z-index: 3; pointer-events: none; }
.player__badges { position: absolute; top: 10px; left: 10px; display: flex; gap: 6px; z-index: 4; }
.player__badge {
  padding: 3px 8px; border-radius: 4px; background: rgba(7,8,26,0.65); backdrop-filter: blur(4px);
  font-size: 11px; color: var(--text); font-family: var(--font-num);
}
.player__badge--live { background: var(--danger); color: #fff; font-weight: 700; }
.player__badge--live .dot { display: inline-block; width: 5px; height: 5px; background: #fff; border-radius: 50%; margin-right: 4px; animation: pulse 1s infinite; }
.player__ctrls { position: absolute; right: 10px; bottom: 10px; display: flex; flex-direction: column; gap: 5px; z-index: 4; }
.ctrl-btn {
  width: 42px; height: 42px; border-radius: 10px; border: none;
  background: rgba(7,8,26,0.65); backdrop-filter: blur(6px);
  font-size: 18px; cursor: pointer; transition: all 0.2s;
}
.ctrl-btn:hover { background: rgba(138,99,255,0.4); transform: scale(1.05); }
.ctrl-btn.is-off { opacity: 0.5; filter: grayscale(0.4); }

/* ====== 秒杀管理卡 ====== */
.seckill-panel { padding: 14px 16px; flex-shrink: 0; }
.sp-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 10px; }
.sp-head h3 { margin: 0; font-size: 14px; font-family: var(--font-display); letter-spacing: 0.05em; color: var(--text-strong); }
.sp-head__actions { display: flex; align-items: center; gap: 8px; }
.sp-status-tag {
  font-size: 11px; padding: 2px 8px; border-radius: 999px;
  font-family: var(--font-display); letter-spacing: 0.08em;
}
.sp-status-tag--0 { color: var(--text-muted); background: rgba(138,99,255,0.15); border: 1px solid var(--border-soft); }
.sp-status-tag--1 { color: var(--seckill-from); background: rgba(255,77,79,0.15); border: 1px solid rgba(255,77,79,0.4); }
.sp-status-tag--2 { color: var(--text-dim); background: rgba(42,44,69,0.4); border: 1px solid var(--border-faint); }
.sp-status-tag--3 { color: var(--text-dim); background: rgba(42,44,69,0.4); border: 1px solid var(--border-faint); text-decoration: line-through; }
.sp-product { display: flex; gap: 10px; margin-bottom: 10px; align-items: center; }
.sp-product__thumb { width: 48px; height: 48px; border-radius: 8px; flex-shrink: 0; }
.sp-product__info { flex: 1; min-width: 0; }
.sp-product__name { font-size: 13px; font-weight: 600; color: var(--text-strong); display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.sp-product__price { display: flex; align-items: baseline; gap: 6px; margin-top: 2px; }
.sp-product__price .num { font-family: var(--font-num); font-size: 20px; font-weight: 700; color: var(--seckill-to); }
.sp-product__orig { font-size: 12px; color: var(--text-dim); text-decoration: line-through; }
.sp-product__discount { font-size: 10px; padding: 1px 5px; border-radius: 3px; background: rgba(255,77,79,0.2); color: var(--seckill-from); }
.sp-meta { display: flex; justify-content: space-between; font-size: 11px; color: var(--text-dim); margin-top: 6px; }
.sp-empty { padding: 20px 0; text-align: center; color: var(--text-dim); font-size: 13px; }

/* ====== 评论区（竖排滚动） ====== */
.comment-panel { flex: 1; display: flex; flex-direction: column; min-height: 0; padding: 12px 14px; overflow: hidden; }
.cp-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px; flex-shrink: 0; }
.cp-head h3 { margin: 0; font-size: 13px; font-family: var(--font-display); letter-spacing: 0.05em; color: var(--text-strong); }
.cp-count { font-size: 12px; color: var(--neon-cyan); }
.cp-list {
  flex: 1; overflow-y: auto; display: flex; flex-direction: column; gap: 6px;
  padding-right: 4px; min-height: 0;
}
.cp-item {
  font-size: 13px; line-height: 1.5;
  padding: 4px 8px; border-radius: 6px;
  animation: float-up 0.3s var(--ease-out-expo) both;
}
.cp-item.is-gift { background: rgba(255,138,0,0.1); border: 1px solid rgba(255,200,100,0.2); }
.cp-item.is-self { background: rgba(0,229,255,0.08); border: 1px solid var(--neon-cyan-soft); }
.cp-item__user { font-weight: 600; margin-right: 5px; white-space: nowrap; }
.cp-item__text { color: var(--text); word-break: break-word; }
.cp-item__gift { color: var(--seckill-to); }
.cp-input { display: flex; gap: 6px; margin-top: 8px; flex-shrink: 0; }
.cp-input input { flex: 1; height: 34px; padding: 0 10px; border-radius: 8px; background: transparent; border: 1px solid var(--border-soft); color: var(--text-strong); outline: none; font-size: 13px; }
.cp-input input:focus { border-color: var(--neon-purple); box-shadow: 0 0 0 2px var(--neon-purple-soft); }
.cp-input button { padding: 0 12px; height: 34px; border-radius: 8px; background: linear-gradient(135deg, var(--neon-purple), #b07cff); color: #fff; font-weight: 600; font-size: 12px; font-family: var(--font-display); border: none; cursor: pointer; }
.cp-input button:disabled { opacity: 0.4; cursor: not-allowed; }

.comment-enter-active { transition: all 0.3s var(--ease-out-expo); }
.comment-enter-from { opacity: 0; transform: translateY(8px); }

/* ====== 右侧下半：数据 + 贡献榜 ====== */
.side-bottom { display: flex; flex-direction: column; gap: 8px; flex-shrink: 0; }

.stats-panel { padding: 10px 12px; }
.stats-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 6px; }
.stat { display: flex; flex-direction: column; align-items: center; gap: 2px; padding: 8px 4px; border-radius: 8px; background: rgba(7,8,26,0.4); border: 1px solid var(--border-faint); }
.stat__val { font-family: var(--font-num); font-size: 16px; font-weight: 700; color: var(--text-strong); }
.stat__lbl { font-size: 10px; color: var(--text-dim); letter-spacing: 0.08em; }
.stat--revenue .stat__val { color: var(--warning); }

.rank-panel { padding: 10px 12px; }
.rp-head { font-size: 12px; font-family: var(--font-display); color: var(--text-strong); letter-spacing: 0.06em; margin-bottom: 6px; }
.rp-list { max-height: 200px; overflow-y: auto; display: flex; flex-direction: column; gap: 3px; }
.rp-row { display: flex; align-items: center; gap: 8px; font-size: 12px; padding: 3px 0; }
.rp-row__rank { width: 18px; text-align: center; font-family: var(--font-num); font-weight: 700; color: var(--text-muted); }
.rp-row__rank.is-top { color: var(--warning); }
.rp-row__name { flex: 1; color: var(--text); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.rp-row__score { color: var(--neon-cyan); font-weight: 700; }

/* ====== 底部 ====== */
.console__foot {
  display: flex; align-items: center; justify-content: space-between;
  padding: 8px 16px;
  border-radius: var(--radius);
  background: rgba(7,8,26,0.4);
  border: 1px solid var(--border-faint);
  flex-shrink: 0;
  font-size: 11px;
  color: var(--text-muted);
}
.foot__info { display: flex; gap: 16px; }
.foot__toggle { background: none; border: none; color: var(--neon-cyan); font-size: 11px; cursor: pointer; font-family: var(--font-display); letter-spacing: 0.08em; }

/* ====== 设置面板（折叠） ====== */
.settings {
  padding: 16px 20px;
  display: flex; flex-direction: column; gap: 14px;
  flex-shrink: 0;
}
.settings__grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; }
.s-field { display: flex; flex-direction: column; gap: 4px; font-size: 11px; color: var(--text-dim); }
.s-field input, .s-field select {
  height: 32px; padding: 0 8px;
  background: rgba(7,8,26,0.6); border: 1px solid var(--border-faint); border-radius: 6px;
  color: var(--text); font-size: 12px;
}
.s-field input:focus, .s-field select:focus { outline: none; border-color: var(--neon-purple); }
.s-field input:disabled, .s-field select:disabled { opacity: 0.5; }
.cover-row { display: flex; gap: 6px; }
.cover-dot { width: 36px; height: 22px; border-radius: 4px; border: 2px solid transparent; cursor: pointer; transition: transform 0.15s, border-color 0.15s; }
.cover-dot:hover { transform: scale(1.08); }
.cover-dot.on { border-color: var(--neon-cyan); box-shadow: 0 0 6px var(--neon-cyan-soft); }
.settings__toggles { display: flex; gap: 20px; }
.toggle { display: flex; align-items: center; gap: 6px; font-size: 12px; color: var(--text); cursor: pointer; }
.toggle input[type="checkbox"] { accent-color: var(--neon-purple); }

/* ====== 弹窗 ====== */
.dlg-mask {
  position: fixed; inset: 0; z-index: var(--z-modal);
  background: rgba(7,8,26,0.65); backdrop-filter: blur(4px);
  display: grid; place-items: center; padding: 24px;
}
.dlg {
  width: min(480px, 100%);
  padding: 24px;
  border-radius: var(--radius-lg);
  border: 1px solid var(--border-soft);
  max-height: 90vh; overflow-y: auto;
}
.dlg__head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; }
.dlg__head h3 { margin: 0; font-family: var(--font-display); font-size: 16px; color: var(--text-strong); letter-spacing: 0.06em; }
.dlg__close { width: 28px; height: 28px; border-radius: 6px; border: 1px solid var(--border-faint); background: transparent; color: var(--text-muted); cursor: pointer; font-size: 14px; }
.dlg__close:hover { color: var(--danger); border-color: var(--danger); }
.dlg__form { display: flex; flex-direction: column; gap: 12px; }
.dlg-row { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
.dlg-field { display: flex; flex-direction: column; gap: 4px; font-size: 12px; color: var(--text-dim); }
.dlg-field input { height: 36px; padding: 0 10px; background: rgba(7,8,26,0.6); border: 1px solid var(--border-faint); border-radius: 8px; color: var(--text); font-size: 13px; }
.dlg-field input:focus { outline: none; border-color: var(--neon-purple); box-shadow: 0 0 0 2px var(--neon-purple-soft); }
.dlg__actions { display: flex; gap: 10px; justify-content: flex-end; margin-top: 6px; }
.share-url {
  padding: 10px; background: rgba(7,8,26,0.6); border: 1px solid var(--border-faint);
  border-radius: 8px; font-family: var(--font-num); font-size: 12px; color: var(--neon-cyan); word-break: break-all;
}

/* ====== 动画 ====== */
.slide-enter-active, .slide-leave-active { transition: all 0.3s var(--ease-out-expo); }
.slide-enter-from, .slide-leave-to { opacity: 0; transform: translateY(-10px); }
@keyframes float-up { from { opacity: 0; transform: translateY(6px); } to { opacity: 1; transform: translateY(0); } }
.modal-enter-active, .modal-leave-active { transition: opacity 0.2s; }
.modal-enter-from, .modal-leave-to { opacity: 0; }

/* ====== 响应式 ====== */
@media (max-width: 1000px) {
  .console__body { grid-template-columns: 1fr; }
  .col--side { max-height: 360px; }
  .settings__grid { grid-template-columns: repeat(2, 1fr); }
}

/* ====== 竖向布局 ====== */
.console.is-vertical .console__body { grid-template-columns: 1fr; }
.console.is-vertical .col--side { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; max-height: 280px; }
.console.is-vertical .comment-panel { max-height: 280px; }

.num { font-family: var(--font-num); }
.no-scrollbar::-webkit-scrollbar { width: 4px; }
.no-scrollbar::-webkit-scrollbar-thumb { background: rgba(138,99,255,0.25); border-radius: 2px; }
</style>