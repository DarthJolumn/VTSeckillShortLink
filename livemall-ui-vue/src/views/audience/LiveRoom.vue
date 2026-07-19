<template>
  <div class="room">
    <!-- 顶栏 -->
    <header class="room__bar">
      <RouterLink to="/" class="back" aria-label="返回广场">◀</RouterLink>
      <div class="room__title">
        <span class="live-dot anim-breathe" /> 直播中
        <span class="room__name">{{ room.title }}</span>
        <span class="room__anchor">· {{ room.anchorName }}</span>
      </div>
      <div class="room__meta">
        <span class="conn" :class="`conn--${live.status}`" :title="connTitle">{{ connLabel }}</span>
        <span class="viewers">
          <span class="num"><NumberFlip :value="live.online" /></span> 观看
        </span>
      </div>
    </header>

    <!-- 主体三栏 -->
    <div class="room__body">
      <!-- 左：视频 + 秒杀卡 -->
      <section class="col col--left">
        <div class="player">
          <div class="player__inner">
            <!-- 视频播放 — 加载失败时自动降级到 CSS 动画 -->
            <video
              v-if="videoReady"
              ref="videoRef"
              class="player__video"
              :src="VIDEO_SRC"
              autoplay muted loop playsinline
              @error="onVideoError"
            />
            <div v-if="!videoReady" class="player__fallback">
              <div class="player__perspective" aria-hidden="true">
                <div class="player__grid" />
                <div class="player__floor" />
              </div>
              <div class="player__scan" aria-hidden="true" />
              <div class="player__vignette" aria-hidden="true" />
              <div class="player__center">
                <div class="player__pulse" />
                <div class="player__hint">LIVE · 视频素材加载中</div>
                <div class="player__quality">
                  <span class="player__quality-dot" /> 1080P · 60fps
                </div>
              </div>
            </div>
            <BarrageTrack class="player__barrage" :messages="live.barrage" :lanes="5" />
          </div>
          <div class="player__badges">
            <span class="player__badge"><span class="live-dot" /> LIVE</span>
            <span class="player__badge player__badge--ghost">
              <svg viewBox="0 0 24 24" width="12" height="12" aria-hidden="true"><path d="M12 4.5C7 4.5 2.7 7.6 1 12c1.7 4.4 6 7.5 11 7.5s9.3-3.1 11-7.5C21.3 7.6 17 4.5 12 4.5Zm0 12.5a5 5 0 1 1 0-10 5 5 0 0 1 0 10Zm0-8a3 3 0 1 0 0 6 3 3 0 0 0 0-6Z" fill="currentColor"/></svg>
              {{ live.online.toLocaleString() }}
            </span>
          </div>
        </div>

        <!-- 秒杀卡 -->
        <div class="seckill-card glass">
          <div class="seckill-card__head">
            <span class="seckill-card__tag">⚡ 限时秒杀</span>
            <CountDown v-if="phase === 'pending'" :target="activityEndAt" @finish="phase = 'running'" />
            <span v-else class="seckill-card__tag seckill-card__tag--live">进行中</span>
          </div>
          <div class="seckill-card__product">
            <div class="seckill-card__thumb" :style="{ background: thumbColor }" />
            <div class="seckill-card__info">
              <h3>{{ activity.name }}</h3>
              <div class="price">
                <span class="price__now">¥{{ activity.price }}</span>
                <span class="price__orig">¥{{ activity.origPrice }}</span>
                <span class="price__off">{{ discount }}折</span>
              </div>
            </div>
          </div>
          <StockBar :stock="live.stock || activity.stock" :total="activity.stockTotal" />
          <div class="seckill-card__btn-wrap">
            <SeckillButton
              :phase="phase"
              :loading="ordering"
              :disabled="phase === 'soldout' || phase === 'ended' || phase === 'pending'"
              @click="onSeckill"
            >
              <template #countdown>
                <CountDown :target="activityStartAt" @finish="phase = 'running'" />
              </template>
            </SeckillButton>
            <div v-if="phase === 'pending'" class="seckill-card__charge" aria-hidden="true">
              <div class="seckill-card__charge-fill" :style="{ width: chargePct + '%' }" />
            </div>
          </div>
          <p class="seckill-card__tip">{{ tipText }}</p>
        </div>
      </section>

      <!-- 中：弹幕/操作 -->
      <section class="col col--mid">
        <div class="panel glass">
          <div class="panel__head">
            <h3>实时弹幕</h3>
            <span class="panel__count num">{{ live.barrage.length }}</span>
          </div>
          <div class="barrage-list no-scrollbar" ref="listRef">
            <transition-group name="barrage-list">
              <div v-for="b in live.barrage.slice(-40)" :key="b.timestamp + '-' + b.userId + '-' + b.content"
                class="barrage-list__item" :class="{ 'is-self': b.self, 'is-gift': !!b.giftName }">
                <span class="barrage-list__user" :style="{ color: pickColor(b.userId) }">{{ b.username }}</span>
                <template v-if="b.giftName">
                  <span class="barrage-list__gift">送出 {{ b.giftName }} ×{{ b.quantity }} {{ b.giftIcon }}</span>
                </template>
                <span v-else class="barrage-list__content">{{ b.content }}</span>
              </div>
            </transition-group>
          </div>

          <!-- 输入栏 -->
          <div class="composer">
            <form class="composer__input" @submit.prevent="onSend">
              <input v-model="draft" type="text" placeholder="发个弹幕聊聊…" maxlength="40" />
              <button type="submit" :disabled="!draft.trim()">发送</button>
              <button type="button" class="composer__gift-btn" @click="giftDrawerOpen = true">
                <span class="composer__gift-icon">🎁</span>
                <span>礼物</span>
              </button>
            </form>
            <div class="composer__hint">点赞 +0.5 · 弹幕 +1.0 · 送礼 +礼物价 × 1.0（见 2.5 排行榜权重）</div>
          </div>
        </div>
      </section>

      <!-- 右：排行榜 -->
      <aside class="col col--right">
        <div class="panel glass rank-panel">
          <div class="panel__head">
            <h3>🏆 实时榜</h3>
            <span class="panel__hint">送礼实时加分</span>
          </div>
          <RankPodium v-if="live.leaderboard.length >= 3" :items="live.leaderboard.slice(0, 3)" />
          <TransitionGroup name="rank" tag="ol" class="rank-list">
            <RankRow v-for="(u, i) in live.leaderboard.slice(3, 12)" :key="u.id"
              :item="u" :rank="i + 4" :prev-rank="prevRanks.get(u.id) ?? null"
              :is-me="u.id === 0" />
          </TransitionGroup>
          <div class="rank-panel__me">
            <span>我的排名</span>
            <span class="num">{{ myRankLabel }}</span>
          </div>
        </div>
      </aside>
    </div>

    <!-- 踢人/封禁/下播 弹窗 -->
    <transition name="modal">
      <div v-if="live.kicked" class="kick-mask" @click.self="onKickClose">
        <div class="kick glass">
          <div class="kick__icon" :class="{ 'is-ban': live.kicked.ban }">{{ live.kicked.ban ? '⛔' : '⚠️' }}</div>
          <h2>{{ live.kicked.reason }}</h2>
          <p v-if="live.kicked.ban">如有疑问请联系管理员。</p>
          <p v-else-if="live.kicked.closed">直播已结束，去看看别的直播间吧。</p>
          <p v-else>同一账号在别处登录，你已被迫下线。</p>
          <NeonButton variant="ghost" @click="onKickClose">知道了</NeonButton>
        </div>
      </div>
    </transition>

    <!-- 大礼物入场（纯 CSS 动画，已删除 Firework/GiftRain Canvas 粒子以减负） -->
    <BigGift :trigger="bigGiftTick" :payload="bigGiftPayload" />

    <!-- 礼物商城抽屉 -->
    <GiftPanel
      :open="giftDrawerOpen"
      :balance="walletBalance"
      @close="giftDrawerOpen = false"
      @send="onDrawerSend"
    />
  </div>
</template>

<script setup>
import { computed, onMounted, onBeforeUnmount, reactive, ref, watch, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { liveApi } from '@/api/live'
import { seckillApi } from '@/api/seckill'
import { useLiveStore } from '@/stores/live'
import { orderStore } from '@/stores/order'
import { showToast } from '@/utils/toast'
import BarrageTrack from '@/components/base/BarrageTrack.vue'
import CountDown from '@/components/base/CountDown.vue'
import SeckillButton from '@/components/base/SeckillButton.vue'
import StockBar from '@/components/base/StockBar.vue'
import RankRow from '@/components/base/RankRow.vue'
import RankPodium from '@/components/base/RankPodium.vue'
import NumberFlip from '@/components/base/NumberFlip.vue'
import NeonButton from '@/components/base/NeonButton.vue'
import BigGift from '@/components/effect/BigGift.vue'
import GiftPanel from '@/components/base/GiftPanel.vue'

const route = useRoute()
const router = useRouter()
const live = useLiveStore()

// —— 视频播放 ——
const videoRef = ref(null)
const videoReady = ref(false)
const VIDEO_SRC = '/demo.mp4'
let localStream = null

async function tryLoadVideo() {
  console.log('[LiveRoom] tryLoadVideo 开始, mediaDevices=', !!navigator.mediaDevices)
  // 优先：摄像头作为直播画面
  try {
    localStream = await navigator.mediaDevices.getUserMedia({
      video: { width: 1280, height: 720 },
      audio: false,
    })
    if (videoRef.value) videoRef.value.srcObject = localStream
    videoReady.value = true
    console.log('[LiveRoom] 摄像头已就绪')
    return
  } catch (e) {
    console.warn('[LiveRoom] 摄像头不可用:', e.name, e.message)
    // 常见原因：NotAllowedError(未授权) / NotFoundError(无摄像头) / NotReadableError(被占用)
  }

  // 降级：探测 demo.mp4
  try {
    const r = await fetch(VIDEO_SRC, { method: 'HEAD' })
    if (r.ok) videoReady.value = true
  } catch { /* 保持 CSS 降级 */ }
}

// —— 房间信息 ——
const room = reactive({ title: '直播间', anchorName: '主播', coverColor: '#12132a' })
async function loadRoomInfo() {
  try {
    const r = await liveApi.getRoom(Number(route.params.roomId))
    Object.assign(room, {
      title: r.title,
      anchorName: r.anchorName,
      coverColor: r.coverColor || '#12132a',
    })
  } catch { /* 保持默认 */ }
}

// —— 活动（mock，待对接 seckillApi） ——
const activityStartAt = ref(Date.now() + 8000) // 8s 后开抢，便于演示 pending→running
const activityEndAt = ref(Date.now() + 8 * 60 * 1000)
const activity = reactive({
  name: '旗舰降噪耳机 · 限量500台',
  price: 299,
  origPrice: 899,
  stock: 480,
  stockTotal: 500,
})
const thumbColor = 'linear-gradient(135deg,#6b4dff,#00e5ff)'

const phase = ref('pending')     // pending / running / soldout / ended
const ordering = ref(false)
const lastOrderAt = ref(0)
const bigGiftTick = ref(0)
const bigGiftPayload = ref({})
const giftDrawerOpen = ref(false)
const walletBalance = ref(8888) // 演示钱包余额（后端就绪后改为 walletApi）

const discount = computed(() => Math.round((activity.price / activity.origPrice) * 10))
const tipText = computed(() => {
  switch (phase.value) {
    case 'pending': return '活动即将开始，倒计时结束后按钮点亮'
    case 'running': return ordering.value ? '正在抢购，请稍候…' : '拼手速！点击立即抢购'
    case 'soldout': return '库存已被抢空，关注下场'
    case 'ended': return '活动已结束'
    default: return ''
  }
})

// —— 秒杀按钮蓄力进度（pending 阶段，从 0 → 100） ——
const chargePct = ref(0)
let chargeStartAt = 0
watch(phase, (p) => {
  if (p === 'pending') {
    chargeStartAt = Date.now()
    const total = activityStartAt.value - chargeStartAt
    const t = setInterval(() => {
      const elapsed = Date.now() - chargeStartAt
      chargePct.value = Math.min(100, (elapsed / total) * 100)
      if (chargePct.value >= 100 || phase.value !== 'pending') clearInterval(t)
    }, 200)
  } else {
    chargePct.value = p === 'running' ? 100 : 0
  }
}, { immediate: true })

// —— 排行榜：记录上一帧排名用于趋势 ——
const prevRanks = reactive(new Map())
watch(() => live.leaderboard.length, () => {
  // 更新前先存上一帧
  nextTick(() => {
    const cur = new Map()
    live.leaderboard.forEach((u, i) => cur.set(u.id, i + 1))
    // 当前帧渲染用的是 prevRanks，渲染后把 prevRanks 更新为当前
    setTimeout(() => {
      prevRanks.clear()
      for (const [k, v] of cur) prevRanks.set(k, v)
    }, 50)
  })
})

const myRankLabel = computed(() => {
  const i = live.leaderboard.findIndex((u) => u.id === 0)
  return i >= 0 ? `第 ${i + 1} 名` : '未上榜'
})

// —— 连接状态 ——
const connLabel = computed(() => ({
  idle: '未连接', connecting: '连接中', open: '已连接',
  reconnecting: '重连中', closed: '已断开',
}[live.status] || ''))
const connTitle = computed(() => `WebSocket: ${live.status}`)

// —— 弹幕发送 ——
const draft = ref('')
const listRef = ref(null)
function onSend() {
  if (!live.sendBarrage(draft.value)) {
    showToast('连接未就绪', 'warning')
    return
  }
  draft.value = ''
  nextTick(() => {
    const el = listRef.value
    if (el) el.scrollTop = el.scrollHeight
  })
}

// —— 送礼（通过抽屉）——
function onDrawerSend({ gift, quantity }) {
  if (!live.sendGift(gift.id, quantity)) {
    showToast('连接未就绪，礼物已缓存待发', 'warning')
    return
  }
  // 扣减演示余额
  walletBalance.value = Math.max(0, walletBalance.value - gift.price * quantity)
}

// 监听送礼广播：大礼物（火箭/跑车）触发全屏入场动画
watch(() => live.giftFeed.length, () => {
  const latest = live.giftFeed[0]
  if (!latest) return
  // 大礼物（≥100 元）触发全屏入场动画
  if (latest.price >= 100) {
    bigGiftPayload.value = {
      icon: latest.giftIcon,
      name: latest.giftName,
      from: latest.self ? '我' : latest.username,
      quantity: latest.quantity,
      price: latest.price,
    }
    bigGiftTick.value++
  }
})

// —— 秒杀 ——
async function onSeckill() {
  if (phase.value !== 'running') return
  if (ordering.value) return
  // 防连点：500ms 锁
  const now = Date.now()
  if (now - lastOrderAt.value < 500) return
  lastOrderAt.value = now

  ordering.value = true
  try {
    const res = await seckillApi.placeOrder(activity.activityId ?? route.params.roomId)
    // res = { result, orderNo }
    if (res.result === 'ok') {
      // Lua 扣减成功，等待 WS gRPC 推送 SEC_KILL_RESULT
      live.expectOrderResult(res.orderNo)
      // 结果由 watch(live.secKillResult) 处理
    } else {
      ordering.value = false
      showToast(res.result || '抢购失败', 'warning')
    }
  } catch (e) {
    ordering.value = false
    // 业务错误码（库存不足等）已在拦截器提示，此处兜底
    if (!e.business) showToast('网络异常，请重试', 'error')
  }
}

// 监听秒杀结果（来自 WS gRPC 推送，按 orderNo 匹配）
watch(() => live.secKillResult, (r) => {
  if (!r || !live.pendingOrderNo) return
  // 只处理匹配当前订单号的推送
  if (r.orderNo !== live.pendingOrderNo && r.reqId !== live.pendingOrderNo) return
  live.pendingOrderNo = null
  ordering.value = false

  if (r.ok) {
    // 后端已通过 Kafka 异步创建订单，前端生成本地订单用于展示
    const order = orderStore.create({
      id: route.params.roomId,
      name: activity.name,
      price: activity.price,
      origPrice: activity.origPrice,
    })
    phase.value = 'soldout'
    showToast('抢购成功！3 秒后跳转订单', 'success')
    setTimeout(() => router.push(`/orders/${order.orderNo}`), 3000)
  } else if (r.reason === 'soldout' || live.stock <= 0) {
    phase.value = 'soldout'
    showToast(r.message || '已售罄', 'warning')
  } else {
    showToast(r.message || '手速慢了，再点一次', 'warning')
  }
})

// 库存为 0 时自动售罄
watch(() => live.stock, (v) => {
  if (v <= 0 && phase.value === 'running') phase.value = 'soldout'
})

// —— 踢人 ——
function onKickClose() {
  live.leave()
  router.replace('/')
}

// —— 颜色 ——
const COLORS = ['#8a63ff', '#00e5ff', '#ff7ad9', '#52e5a4', '#ffcb55', '#4cc9f0']
function pickColor(id) { return COLORS[Math.abs(id) % COLORS.length] || '#e5e6f0' }

function onVideoError() {
  videoReady.value = false
}

onMounted(() => {
  loadRoomInfo()
  tryLoadVideo()
  live.join(Number(route.params.roomId) || 1)
})
onBeforeUnmount(() => {
  if (localStream) { localStream.getTracks().forEach(t => t.stop()); localStream = null }
  live.leave()
})
</script>

<style scoped>
.room { display: flex; flex-direction: column; height: calc(100vh - 60px); min-height: 640px; }

/* 顶栏 */
.room__bar {
  display: flex; align-items: center; gap: 16px;
  padding: 12px 24px;
  border-bottom: 1px solid var(--border-faint);
  background: rgba(7, 8, 26, 0.6);
  backdrop-filter: blur(10px);
}
.back { color: var(--text-muted); font-size: 14px; padding: 4px 8px; border-radius: 6px; transition: color 0.2s, background 0.2s; }
.back:hover { color: var(--neon-cyan); background: var(--bg-card); }
.room__title { display: flex; align-items: center; gap: 8px; font-family: var(--font-display); }
.live-dot { width: 8px; height: 8px; border-radius: 50%; background: var(--seckill-from); box-shadow: 0 0 10px var(--seckill-from); display: inline-block; }
.room__name { color: var(--text-strong); font-weight: 600; margin-left: 4px; }
.room__anchor { color: var(--text-muted); font-size: 13px; }
.room__meta { margin-left: auto; display: flex; align-items: center; gap: 16px; }
.conn {
  font-size: 12px; padding: 4px 10px; border-radius: 999px;
  border: 1px solid var(--border-soft); background: var(--bg-card);
  font-family: var(--font-display); letter-spacing: 0.1em;
}
.conn--open { color: var(--success); border-color: rgba(82, 229, 164, 0.4); box-shadow: 0 0 8px rgba(82, 229, 164, 0.3); }
.conn--connecting, .conn--reconnecting { color: var(--warning); animation: breathe 1.4s ease-in-out infinite; }
.conn--closed, .conn--idle { color: var(--danger); }
.viewers { font-size: 14px; color: var(--text-muted); }
.viewers .num { color: var(--text-strong); font-weight: 700; font-size: 16px; }

/* 主体 */
.room__body {
  flex: 1;
  display: grid;
  grid-template-columns: minmax(0, 1.6fr) minmax(320px, 1fr) 300px;
  gap: 16px;
  padding: 16px 24px 24px;
  min-height: 0;
}
.col { display: flex; flex-direction: column; gap: 14px; min-height: 0; animation: float-up 0.5s var(--ease-out-expo) both; }
.col--left { animation-delay: 0.05s; }
.col--mid { min-width: 0; animation-delay: 0.18s; }
.col--right { min-width: 0; animation-delay: 0.3s; }

/* 玻璃面板共用 */
.panel { display: flex; flex-direction: column; padding: 14px 16px; min-height: 0; }
.panel__head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 10px; }
.panel__head h3 { font-size: 15px; letter-spacing: 0.04em; }
.panel__count { color: var(--neon-cyan); font-size: 13px; }
.panel__hint { font-size: 11px; color: var(--text-dim); letter-spacing: 0.1em; font-family: var(--font-display); }

/* —— 左：视频（透视化） —— */
.player { position: relative; aspect-ratio: 16/9; border-radius: var(--radius-lg); overflow: hidden; border: 1px solid var(--border-soft); box-shadow: 0 12px 40px rgba(0,0,0,0.4); }
.player__inner { position: relative; width: 100%; height: 100%; background: #050617; }
.player__video {
  position: absolute; inset: 0;
  width: 100%; height: 100%;
  object-fit: cover;
}
.player__fallback { position: absolute; inset: 0; }

/* 透视层：网格 + 地平线地板 */
.player__perspective {
  position: absolute;
  inset: 0;
  perspective: 600px;
  overflow: hidden;
}
.player__grid {
  position: absolute;
  inset: -10% 0 50% 0;
  height: 60%;
  background-image: linear-gradient(rgba(138, 99, 255, 0.12) 1px, transparent 1px),
    linear-gradient(90deg, rgba(138, 99, 255, 0.12) 1px, transparent 1px);
  background-size: 44px 44px;
  mask-image: linear-gradient(180deg, transparent, #000 30%, #000 80%, transparent);
}
.player__floor {
  position: absolute;
  bottom: 0; left: 50%;
  width: 200%; height: 50%;
  transform: translateX(-50%) rotateX(72deg);
  transform-origin: top center;
  background-image: linear-gradient(rgba(0, 229, 255, 0.22) 1px, transparent 1px),
    linear-gradient(90deg, rgba(0, 229, 255, 0.22) 1px, transparent 1px);
  background-size: 40px 40px;
  mask-image: linear-gradient(180deg, #000, transparent 80%);
}

/* 扫描线（已移除持续动画以降低 GPU 合成开销） */
.player__scan {
  position: absolute; inset: 0;
  background: linear-gradient(180deg, transparent 48%, rgba(0, 229, 255, 0.10) 50%, transparent 52%);
  pointer-events: none;
}

/* 暗角 */
.player__vignette {
  position: absolute; inset: 0;
  background: radial-gradient(closest-side, transparent 55%, rgba(7,8,26,0.65) 100%);
  pointer-events: none;
}

.player__center { position: absolute; inset: 0; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 14px; z-index: 2; }
.player__pulse { width: 78px; height: 78px; border-radius: 50%; border: 2px solid var(--neon-purple); animation: radar-spin 3s linear infinite; box-shadow: 0 0 24px var(--neon-purple-soft); position: relative; }
.player__pulse::before { content: ''; position: absolute; inset: -8px; border-radius: 50%; border: 1px solid var(--neon-purple-soft); opacity: 0.6; }
.player__pulse::after { content: ''; position: absolute; inset: 14px; border-radius: 50%; border: 1px solid var(--neon-cyan); }
.player__hint { font-family: var(--font-display); letter-spacing: 0.24em; color: var(--text-muted); font-size: 12px; }
.player__quality { display: inline-flex; align-items: center; gap: 6px; font-family: var(--font-display); font-size: 10px; letter-spacing: 0.18em; color: var(--neon-cyan); padding: 3px 8px; border-radius: 4px; border: 1px solid var(--neon-cyan-soft); background: rgba(0,229,255,0.06); }
.player__quality-dot { width: 5px; height: 5px; border-radius: 50%; background: var(--neon-cyan); box-shadow: 0 0 6px var(--neon-cyan); animation: breathe 1.4s ease-in-out infinite; }

.player__barrage { position: absolute; inset: 0; z-index: 3; }

/* 徽标组 */
.player__badges { position: absolute; top: 12px; left: 12px; display: flex; gap: 8px; z-index: 4; }
.player__badge { padding: 4px 10px; border-radius: 6px; background: rgba(0,0,0,0.55); backdrop-filter: blur(6px); font-family: var(--font-display); font-size: 11px; letter-spacing: 0.16em; display: inline-flex; align-items: center; gap: 6px; color: #fff; }
.player__badge--ghost { color: var(--text-muted); }
.player__badge--ghost svg { color: var(--neon-cyan); }

/* —— 秒杀卡 —— */
.seckill-card { padding: 16px; }
.seckill-card__head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; }
.seckill-card__tag { font-family: var(--font-display); font-size: 12px; letter-spacing: 0.16em; color: var(--seckill-to); padding: 4px 10px; border-radius: 6px; background: rgba(255, 138, 0, 0.12); border: 1px solid rgba(255, 138, 0, 0.3); }
.seckill-card__tag--live { color: var(--seckill-from); background: rgba(255, 77, 79, 0.12); border-color: rgba(255, 77, 79, 0.4); }
.seckill-card__product { display: flex; gap: 12px; margin-bottom: 12px; }
.seckill-card__thumb { width: 64px; height: 64px; border-radius: 10px; flex-shrink: 0; }
.seckill-card__info { flex: 1; min-width: 0; }
.seckill-card__info h3 { font-size: 15px; margin-bottom: 6px; }
.price { display: flex; align-items: baseline; gap: 8px; }
.price__now { font-family: var(--font-num); font-size: 24px; font-weight: 800; color: var(--seckill-to); }
.price__orig { font-size: 13px; color: var(--text-dim); text-decoration: line-through; }
.price__off { font-size: 11px; padding: 2px 6px; border-radius: 4px; background: rgba(255, 77, 79, 0.2); color: var(--seckill-from); }
.seckill-card__tip { margin-top: 10px; font-size: 12px; color: var(--text-dim); text-align: center; }

/* 秒杀按钮 + 蓄力条 */
.seckill-card__btn-wrap { position: relative; margin-top: 14px; }
.seckill-card__charge {
  position: absolute;
  left: 0; right: 0; bottom: -8px;
  height: 3px;
  border-radius: 999px;
  background: var(--bg-card);
  overflow: hidden;
}
.seckill-card__charge-fill {
  height: 100%;
  background: linear-gradient(90deg, var(--neon-purple), var(--neon-cyan));
  box-shadow: 0 0 8px var(--neon-cyan-soft);
  transition: width 0.2s linear;
}

/* —— 中：弹幕列表 —— */
.col--mid .panel { flex: 1; }
.barrage-list { flex: 1; overflow-y: auto; display: flex; flex-direction: column; gap: 6px; padding-right: 4px; }
.barrage-list__item {
  font-size: 13px; line-height: 1.6;
  padding: 4px 8px; border-radius: 6px;
  animation: float-up 0.3s var(--ease-out-expo) both;
}
.barrage-list__item.is-self { background: rgba(0, 229, 255, 0.08); border: 1px solid var(--neon-cyan-soft); }
.barrage-list__item.is-gift { background: rgba(255, 138, 0, 0.1); border: 1px solid rgba(255, 200, 100, 0.3); }
.barrage-list__user { font-weight: 600; margin-right: 6px; }
.barrage-list__content { color: var(--text); }
.barrage-list__gift { color: var(--seckill-to); }

.barrage-list-enter-active { transition: all 0.3s var(--ease-out-expo); }
.barrage-list-enter-from { opacity: 0; transform: translateY(8px); }

/* composer */
.composer { border-top: 1px solid var(--border-faint); padding-top: 10px; margin-top: 8px; }
.composer__input { display: flex; gap: 8px; align-items: center; }
.composer__input input { flex: 1; height: 38px; padding: 0 12px; border-radius: 8px; background: transparent; border: 1px solid var(--border-soft); color: var(--text-strong); outline: none; transition: border-color 0.2s, box-shadow 0.2s; }
.composer__input input:focus { border-color: var(--neon-purple); box-shadow: 0 0 0 3px var(--neon-purple-soft); }
.composer__input button[type="submit"] { padding: 0 16px; height: 38px; border-radius: 8px; background: linear-gradient(135deg, var(--neon-purple), #b07cff); color: #fff; font-family: var(--font-display); font-weight: 600; letter-spacing: 0.08em; transition: filter 0.2s, transform 0.16s; }
.composer__input button[type="submit"]:not(:disabled):hover { filter: brightness(1.1); }
.composer__input button[type="submit"]:not(:disabled):active { transform: translateY(1px); }
.composer__input button[type="submit"]:disabled { opacity: 0.4; cursor: not-allowed; }
.composer__gift-btn {
  display: inline-flex; align-items: center; gap: 6px;
  height: 38px; padding: 0 14px;
  border-radius: 8px;
  border: 1px solid var(--border-soft);
  background: linear-gradient(135deg, rgba(255,84,112,0.12), rgba(255,138,0,0.12));
  color: var(--text-strong);
  font-family: var(--font-display);
  font-weight: 600;
  font-size: 13px;
  letter-spacing: 0.06em;
  cursor: pointer;
  transition: all 0.2s;
}
.composer__gift-btn:hover {
  border-color: var(--danger);
  box-shadow: 0 0 12px rgba(255,84,112,0.3);
  transform: translateY(-1px);
}
.composer__gift-icon {
  font-size: 18px;
  filter: drop-shadow(0 0 4px rgba(255,138,0,0.5));
}
.composer__hint { margin-top: 6px; font-size: 11px; color: var(--text-dim); }

/* —— 右：排行榜 —— */
.rank-panel { height: 100%; }
.rank-list { flex: 1; overflow-y: auto; display: flex; flex-direction: column; gap: 2px; padding-right: 2px; position: relative; }
/* FLIP 顺位滑动：v-for 配 TransitionGroup name="rank" */
.rank-move { transition: transform 0.45s var(--ease-out-expo); }
.rank-enter-active { transition: all 0.4s var(--ease-out-expo); }
.rank-leave-active { transition: all 0.3s var(--ease-out-expo); position: absolute; left: 0; right: 0; }
.rank-enter-from { opacity: 0; transform: translateX(20px); }
.rank-leave-to { opacity: 0; transform: translateX(-20px); }
.rank-panel__me { margin-top: 10px; padding: 10px 12px; border-radius: 10px; background: linear-gradient(90deg, rgba(0,229,255,0.12), transparent); border: 1px solid var(--neon-cyan-soft); display: flex; justify-content: space-between; align-items: center; font-size: 13px; }
.rank-panel__me .num { color: var(--neon-cyan); font-weight: 700; font-size: 16px; }

/* —— 踢人弹窗 —— */
.kick-mask { position: fixed; inset: 0; background: rgba(7,8,26,0.7); backdrop-filter: blur(8px); z-index: var(--z-modal); display: grid; place-items: center; padding: 24px; }
.kick { width: min(420px, 100%); padding: 32px 28px; text-align: center; display: flex; flex-direction: column; align-items: center; gap: 12px; }
.kick__icon { font-size: 48px; line-height: 1; filter: drop-shadow(0 0 12px rgba(255,77,79,0.5)); }
.kick__icon.is-ban { filter: drop-shadow(0 0 12px rgba(255,77,79,0.7)); }
.kick h2 { font-size: 22px; }
.kick p { color: var(--text-muted); font-size: 14px; }

.modal-enter-active, .modal-leave-active { transition: opacity 0.25s var(--ease-out-expo); }
.modal-enter-from, .modal-leave-to { opacity: 0; }

/* —— 响应式 —— */
@media (max-width: 1080px) {
  .room__body { grid-template-columns: 1fr; grid-auto-rows: auto; }
  .col--right { order: 2; }
  .player { aspect-ratio: 16/9; }
}
</style>
