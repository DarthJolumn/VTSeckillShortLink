<template>
  <div class="dashboard">
    <!-- 背景扫描线 -->
    <div class="scan-line" />
    <div class="grid-bg" />

    <!-- 顶部状态条 -->
    <header class="screen-head">
      <div class="screen-head__left">
        <span class="live-dot" />
        <h1 class="screen-head__title">LiveMall 实时作战大屏</h1>
        <span class="screen-head__badge">LIVE</span>
      </div>
      <div class="screen-head__right">
        <span class="heartbeat"><i class="heartbeat__pulse" /> 心跳正常</span>
        <span class="screen-head__time num">{{ clock }}</span>
        <span class="screen-head__updated">更新于 {{ lastUpdate }}</span>
      </div>
    </header>

    <!-- KPI 大屏 -->
    <div class="kpi-row">
      <div v-for="k in kpis" :key="k.key" class="kpi glass" :class="`kpi--${k.key}`">
        <div class="kpi__head">
          <span class="kpi__icon">{{ k.icon }}</span>
          <span class="kpi__label">{{ k.label }}</span>
        </div>
        <span class="kpi__value">
          <span v-if="k.prefix" class="kpi__prefix">{{ k.prefix }}</span>
          <NumberFlip :value="k.value" :urgent="k.urgent" />
        </span>
        <span class="kpi__delta" :class="k.delta >= 0 ? 'up' : 'down'">
          {{ k.delta >= 0 ? '▲' : '▼' }} {{ Math.abs(k.delta).toFixed(1) }}%
          <span class="kpi__hint">{{ k.hint }}</span>
        </span>
      </div>

      <!-- QPS 实时折线（独立大卡） -->
      <div class="kpi kpi--qps glass">
        <div class="kpi__head">
          <span class="kpi__icon">⚡</span>
          <span class="kpi__label">实时 QPS</span>
          <span class="qps__now num">{{ currentQps }}</span>
        </div>
        <div class="qps-chart">
          <svg :viewBox="`0 0 ${QPS_W} ${QPS_H}`" preserveAspectRatio="none">
            <defs>
              <linearGradient id="qps-grad" x1="0" y1="0" x2="0" y2="1">
                <stop offset="0%" stop-color="#ff5470" stop-opacity="0.5" />
                <stop offset="100%" stop-color="#ff5470" stop-opacity="0" />
              </linearGradient>
            </defs>
            <g class="qps-chart__grid">
              <line v-for="i in 4" :key="i" :x1="0" :x2="QPS_W" :y1="(i-1)*QPS_H/3" :y2="(i-1)*QPS_H/3" />
            </g>
            <path :d="qpsAreaPath" fill="url(#qps-grad)" />
            <path :d="qpsPath" class="qps-chart__line" />
            <circle :cx="lastQpsPoint.x" :cy="lastQpsPoint.y" r="3" class="qps-chart__dot" />
          </svg>
          <div class="qps-chart__axis">
            <span>{{ QPS_MAX }}</span>
            <span>0</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 排行榜 + 秒杀战况 -->
    <div class="dual">
      <!-- Top10 排行榜 -->
      <div class="panel glass">
        <div class="panel__head">
          <h3>🏆 人气主播 Top10</h3>
          <span class="panel__hint">FLIP 顺位 · 1s 刷新</span>
        </div>
        <TransitionGroup name="rank" tag="ol" class="rank-list">
          <li v-for="(r, i) in topRank" :key="r.id" class="rank-row" :class="{ 'is-top': i < 3 }">
            <span class="rank-row__no" :class="`no--${i < 3 ? i + 1 : 'other'}`">{{ i + 1 }}</span>
            <div class="rank-row__avatar" :style="{ background: r.avatarBg }">{{ r.name[0] }}</div>
            <div class="rank-row__info">
              <span class="rank-row__name">{{ r.name }}</span>
              <span class="rank-row__room">{{ r.roomTitle }}</span>
            </div>
            <div class="rank-row__bar">
              <div class="rank-row__bar-fill" :style="{ width: (r.score / maxScore * 100) + '%' }" />
            </div>
            <span class="rank-row__score num">{{ r.score.toLocaleString() }}</span>
            <span class="rank-row__delta" :class="r.delta >= 0 ? 'up' : 'down'">
              {{ r.delta >= 0 ? '▲' : '▼' }}{{ Math.abs(r.delta) }}
            </span>
          </li>
        </TransitionGroup>
      </div>

      <!-- 秒杀战况 -->
      <div class="panel glass">
        <div class="panel__head">
          <h3>🔥 秒杀战况</h3>
          <span class="panel__hint">{{ activities.length }} 场进行中</span>
        </div>
        <ul class="sk-list">
          <li v-for="a in activities" :key="a.id" class="sk-row" :class="{ 'is-critical': a.soldPct > 80 }">
            <div class="sk-row__head">
              <span class="sk-row__name">{{ a.name }}</span>
              <span class="sk-row__anchor">@{{ a.anchor }}</span>
            </div>
            <div class="sk-row__bar">
              <div class="sk-row__bar-fill" :style="{ width: a.soldPct + '%' }" />
              <span class="sk-row__bar-text">{{ a.sold }} / {{ a.total }} · ¥{{ a.price }}</span>
            </div>
            <div class="sk-row__meta">
              <span class="sk-row__pct num">{{ a.soldPct.toFixed(0) }}%</span>
              <span class="sk-row__qps num">{{ a.qps }} QPS</span>
              <span class="sk-row__revenue num">¥{{ (a.sold * a.price).toLocaleString() }}</span>
            </div>
          </li>
        </ul>
      </div>
    </div>

    <!-- 各直播间在线柱状图 -->
    <div class="panel glass">
      <div class="panel__head">
        <h3>📊 各直播间在线人数</h3>
        <span class="panel__hint">实时跳动 · 共 {{ rooms.length }} 间直播中</span>
      </div>
      <div class="bar-chart">
        <div v-for="r in rooms" :key="r.id" class="bar-col" :title="`${r.name}：${r.online} 人`">
          <span class="bar-col__value num">{{ r.online }}</span>
          <div class="bar-col__bar">
            <div class="bar-col__fill" :style="{ height: (r.online / maxRoomOnline * 100) + '%', background: r.color }" />
          </div>
          <span class="bar-col__label">{{ r.name }}</span>
        </div>
      </div>
    </div>

    <!-- 实时订单流 + 系统健康度 -->
    <div class="dual">
      <!-- 实时订单流 -->
      <div class="panel glass">
        <div class="panel__head">
          <h3>🧾 实时订单流</h3>
          <span class="panel__hint">最近 {{ orderFeed.length }} 笔</span>
        </div>
        <TransitionGroup name="feed" tag="ul" class="feed-list">
          <li v-for="o in orderFeed" :key="o.id" class="feed-row" :class="`feed-row--${o.status}`">
            <span class="feed-row__time num">{{ o.time }}</span>
            <span class="feed-row__user">{{ o.user }}</span>
            <span class="feed-row__product">{{ o.product }}</span>
            <span class="feed-row__amt num">¥{{ o.amount }}</span>
            <span class="feed-row__status" :class="`st--${o.status}`">{{ statusLabel(o.status) }}</span>
          </li>
        </TransitionGroup>
      </div>

      <!-- 系统健康度 -->
      <div class="panel glass">
        <div class="panel__head">
          <h3>🛡️ 系统健康度</h3>
          <span class="panel__hint">5 微服务实时状态</span>
        </div>
        <ul class="health-list">
          <li v-for="s in services" :key="s.name" class="health-row" :class="`health--${s.status}`">
            <span class="health-row__dot" />
            <div class="health-row__body">
              <span class="health-row__name">{{ s.name }}</span>
              <span class="health-row__desc">{{ s.desc }}</span>
            </div>
            <div class="health-row__metrics">
              <span class="health-row__cpu num">CPU {{ s.cpu }}%</span>
              <span class="health-row__mem num">MEM {{ s.mem }}%</span>
              <span class="health-row__rt num">{{ s.rt }}ms</span>
            </div>
            <span class="health-row__status" :class="`hs--${s.status}`">{{ healthLabel(s.status) }}</span>
          </li>
        </ul>
        <div class="health-foot">
          <span class="health-foot__item ok"><i /> 正常 {{ healthCount.ok }}</span>
          <span class="health-foot__item warn"><i /> 告警 {{ healthCount.warn }}</span>
          <span class="health-foot__item down"><i /> 异常 {{ healthCount.down }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { showToast } from '@/utils/toast'
import NumberFlip from '@/components/base/NumberFlip.vue'

// —— 时钟 ——
const clock = ref('')
const lastUpdate = ref('')
function tickClock() {
  const d = new Date()
  const pad = n => String(n).padStart(2, '0')
  clock.value = `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
  lastUpdate.value = `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

// —— KPI ——
const kpis = ref([
  { key: 'online',  icon: '👥', label: '全站在线', prefix: '',   value: 12345, delta: 8.4,  hint: 'vs 1h 前', urgent: false },
  { key: 'orders',  icon: '📦', label: '今日下单', prefix: '',   value: 8901,  delta: 12.6, hint: 'vs 昨日同时段', urgent: false },
  { key: 'revenue', icon: '💰', label: '成交额',   prefix: '¥',  value: 98210, delta: 18.2, hint: 'vs 昨日全天', urgent: false },
  { key: 'peak',    icon: '⚡', label: '秒杀峰值QPS', prefix: '', value: 1240, delta: 24.1, hint: '近 5 分钟峰值', urgent: true },
])

// —— QPS 实时折线 ——
const QPS_W = 280
const QPS_H = 80
const QPS_MAX = 1500
const qpsSeries = ref(Array.from({ length: 30 }, () => 400 + Math.random() * 300))
const currentQps = computed(() => Math.round(qpsSeries.value[qpsSeries.value.length - 1]))

const qpsPath = computed(() => {
  const arr = qpsSeries.value
  const step = QPS_W / (arr.length - 1)
  return arr.map((v, i) => {
    const x = i * step
    const y = QPS_H - (v / QPS_MAX) * QPS_H * 0.92 - 4
    return `${i === 0 ? 'M' : 'L'}${x.toFixed(1)},${y.toFixed(1)}`
  }).join(' ')
})
const qpsAreaPath = computed(() => {
  const arr = qpsSeries.value
  const step = QPS_W / (arr.length - 1)
  const line = arr.map((v, i) => {
    const x = i * step
    const y = QPS_H - (v / QPS_MAX) * QPS_H * 0.92 - 4
    return `${i === 0 ? 'M' : 'L'}${x.toFixed(1)},${y.toFixed(1)}`
  }).join(' ')
  return `${line} L${QPS_W},${QPS_H} L0,${QPS_H} Z`
})
const lastQpsPoint = computed(() => {
  const arr = qpsSeries.value
  const v = arr[arr.length - 1]
  const step = QPS_W / (arr.length - 1)
  return { x: (arr.length - 1) * step, y: QPS_H - (v / QPS_MAX) * QPS_H * 0.92 - 4 }
})

// —— Top10 排行榜 ——
const AVATAR_COLORS = [
  'linear-gradient(135deg,#8a63ff,#00e5ff)',
  'linear-gradient(135deg,#ff7ad9,#8a63ff)',
  'linear-gradient(135deg,#52e5a4,#00e5ff)',
  'linear-gradient(135deg,#ffcb55,#ff7ad9)',
  'linear-gradient(135deg,#ff5470,#ff8a00)',
  'linear-gradient(135deg,#4cc9f0,#8a63ff)',
]
const topRank = ref([
  { id: 1, name: 'NeonAnchor',   roomTitle: '旗舰降噪耳机秒杀局',     score: 28460, delta: 2, avatarBg: AVATAR_COLORS[0] },
  { id: 2, name: 'GlowQueen',    roomTitle: '美妆最后一小时',         score: 24180, delta: 0, avatarBg: AVATAR_COLORS[1] },
  { id: 3, name: 'SnackKing',    roomTitle: '零食清仓大放送',         score: 21840, delta: -1, avatarBg: AVATAR_COLORS[2] },
  { id: 4, name: 'SneakerX',     roomTitle: '潮鞋限量首发',           score: 19620, delta: 1, avatarBg: AVATAR_COLORS[3] },
  { id: 5, name: 'CyberDan',     roomTitle: '深夜数码秒杀局',         score: 17280, delta: 0, avatarBg: AVATAR_COLORS[4] },
  { id: 6, name: 'PixelPulse',   roomTitle: '机械键盘 RGB 专场',      score: 14920, delta: 3, avatarBg: AVATAR_COLORS[5] },
  { id: 7, name: 'VelvetVoice',  roomTitle: '深夜情感电台',           score: 12840, delta: -2, avatarBg: AVATAR_COLORS[0] },
  { id: 8, name: 'MidnightMike', roomTitle: '智能家电精选',           score: 10620, delta: 0, avatarBg: AVATAR_COLORS[1] },
  { id: 9, name: 'CrystalCara',  roomTitle: '水晶饰品专场',           score: 8480,  delta: 1, avatarBg: AVATAR_COLORS[2] },
  { id: 10, name: 'LunaLite',    roomTitle: '夏夜美妆教程',           score: 6240,  delta: -1, avatarBg: AVATAR_COLORS[3] },
])
const maxScore = computed(() => Math.max(1, ...topRank.value.map(r => r.score)))

// —— 秒杀战况 ——
const activities = ref([
  { id: 'A001', name: '旗舰降噪耳机 · 限量500台', anchor: 'NeonAnchor', price: 299,  sold: 412, total: 500, qps: 86,  soldPct: 82.4 },
  { id: 'A005', name: '智能手表 · 80台',          anchor: 'NeonAnchor', price: 599,  sold: 68,  total: 80,  qps: 142, soldPct: 85.0 },
  { id: 'A006', name: '游戏鼠标 · 300台',         anchor: 'GlowQueen',  price: 149,  sold: 220, total: 300, qps: 64,  soldPct: 73.3 },
  { id: 'A009', name: '蓝牙音箱 · 100台',         anchor: 'SnackKing',  price: 99,   sold: 38,  total: 100, qps: 28,  soldPct: 38.0 },
  { id: 'A010', name: '机械键盘客制化 · 50台',    anchor: 'SneakerX',   price: 1299, sold: 12,  total: 50,  qps: 18,  soldPct: 24.0 },
])

// —— 各直播间柱状图 ——
const ROOM_COLORS = [
  'linear-gradient(180deg,#00e5ff,#8a63ff)',
  'linear-gradient(180deg,#8a63ff,#ff7ad9)',
  'linear-gradient(180deg,#ff7ad9,#ff5470)',
  'linear-gradient(180deg,#ffcb55,#ff8a00)',
  'linear-gradient(180deg,#52e5a4,#00e5ff)',
  'linear-gradient(180deg,#4cc9f0,#8a63ff)',
  'linear-gradient(180deg,#ff5470,#ff8a00)',
  'linear-gradient(180deg,#8a63ff,#4cc9f0)',
]
const rooms = ref([
  { id: 1, name: 'NeonAnchor',   online: 940 },
  { id: 2, name: 'GlowQueen',    online: 720 },
  { id: 3, name: 'SnackKing',    online: 1180 },
  { id: 4, name: 'SneakerX',     online: 1560 },
  { id: 5, name: 'CyberDan',     online: 480 },
  { id: 6, name: 'PixelPulse',   online: 320 },
  { id: 7, name: 'VelvetVoice',  online: 260 },
  { id: 8, name: 'MidnightMike', online: 180 },
].map((r, i) => ({ ...r, color: ROOM_COLORS[i % ROOM_COLORS.length] })))
const maxRoomOnline = computed(() => Math.max(1, ...rooms.value.map(r => r.online)))

// —— 实时订单流 ——
const PRODUCTS = ['降噪耳机', '机械键盘', '4K显示器', '蓝牙音箱', '智能手表', '游戏鼠标', '电竞椅', '机械键盘客制化']
const USERS = ['user_001', 'user_042', 'user_108', 'user_219', 'user_337', 'user_501', 'user_666', 'user_777', 'user_842', 'user_999']
const orderFeed = ref([])
let orderSeq = 0
function genOrder() {
  orderSeq += 1
  const product = PRODUCTS[Math.floor(Math.random() * PRODUCTS.length)]
  const priceMap = { '降噪耳机': 299, '机械键盘': 199, '4K显示器': 1299, '蓝牙音箱': 99, '智能手表': 599, '游戏鼠标': 149, '电竞椅': 899, '机械键盘客制化': 1299 }
  const user = USERS[Math.floor(Math.random() * USERS.length)]
  const d = new Date()
  const pad = n => String(n).padStart(2, '0')
  const statuses = ['paid', 'paid', 'paid', 'pending', 'pending', 'cancel']
  const status = statuses[Math.floor(Math.random() * statuses.length)]
  return {
    id: orderSeq,
    time: `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`,
    user,
    product,
    amount: priceMap[product] || 99,
    status,
  }
}
function statusLabel(s) { return { paid: '已支付', pending: '待支付', cancel: '已取消' }[s] || s }

// 初始化 8 条
for (let i = 0; i < 8; i++) orderFeed.value.push(genOrder())

// —— 系统健康度 ——
const services = ref([
  { name: 'livemall-gateway',     desc: '网关 · WebFlux',     status: 'ok',   cpu: 32, mem: 48, rt: 12 },
  { name: 'livemall-user',        desc: '用户服务 · VT',      status: 'ok',   cpu: 28, mem: 42, rt: 24 },
  { name: 'livemall-websocket',   desc: 'WebSocket · WS 推送', status: 'warn', cpu: 76, mem: 68, rt: 86 },
  { name: 'livemall-seckill',     desc: '秒杀服务 · Redis+Kafka', status: 'ok',   cpu: 54, mem: 60, rt: 42 },
  { name: 'livemall-leaderboard', desc: '排行榜 · ZSet',      status: 'ok',   cpu: 22, mem: 36, rt: 8 },
])
const healthCount = computed(() => {
  const c = { ok: 0, warn: 0, down: 0 }
  services.value.forEach(s => { c[s.status] += 1 })
  return c
})
function healthLabel(s) { return { ok: '正常', warn: '告警', down: '异常' }[s] || s }

// —— 1s 节流刷新 ——
let timer = null
function refresh() {
  // KPI 波动
  kpis.value[0].value += Math.floor((Math.random() - 0.45) * 80)
  kpis.value[0].value = Math.max(8000, kpis.value[0].value)
  kpis.value[1].value += Math.floor(Math.random() * 20)
  kpis.value[2].value += Math.floor(Math.random() * 800)
  kpis.value[3].value = Math.max(800, kpis.value[3].value + Math.floor((Math.random() - 0.4) * 60))

  // QPS 推进
  qpsSeries.value.shift()
  const last = qpsSeries.value[qpsSeries.value.length - 1]
  const next = Math.max(200, Math.min(QPS_MAX - 50, last + (Math.random() - 0.5) * 200))
  qpsSeries.value.push(next)

  // 排行榜分数波动 + 重排
  topRank.value.forEach(r => {
    r.score += Math.floor(Math.random() * 120)
    r.delta = Math.floor((Math.random() - 0.5) * 4)
  })
  topRank.value.sort((a, b) => b.score - a.score)

  // 秒杀进度推进
  activities.value.forEach(a => {
    if (a.sold < a.total) {
      const add = Math.floor(Math.random() * 3)
      a.sold = Math.min(a.total, a.sold + add)
      a.soldPct = (a.sold / a.total) * 100
      a.qps = Math.max(0, a.qps + Math.floor((Math.random() - 0.5) * 20))
    }
  })

  // 直播间在线波动
  rooms.value.forEach(r => {
    r.online = Math.max(60, r.online + Math.floor((Math.random() - 0.45) * 40))
  })

  // 服务指标波动 + 偶发告警
  services.value.forEach(s => {
    s.cpu = Math.max(10, Math.min(95, s.cpu + Math.floor((Math.random() - 0.5) * 8)))
    s.mem = Math.max(20, Math.min(92, s.mem + Math.floor((Math.random() - 0.5) * 6)))
    s.rt = Math.max(4, Math.min(280, s.rt + Math.floor((Math.random() - 0.5) * 12)))
    if (s.cpu > 80 || s.rt > 120) s.status = 'warn'
    else if (s.cpu < 65 && s.rt < 80) s.status = 'ok'
  })

  // 订单流新增
  orderFeed.value.unshift(genOrder())
  if (orderFeed.value.length > 12) orderFeed.value.pop()

  tickClock()
}

onMounted(() => {
  tickClock()
  showToast('实时作战大屏已就绪 · 演示模式', 'info')
  timer = setInterval(refresh, 1000)
})
onUnmounted(() => { if (timer) clearInterval(timer) })
</script>

<style scoped>
.dashboard {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-height: calc(100vh - 48px);
  overflow: hidden;
}

/* —— 背景扫描线 + 网格 —— */
.scan-line {
  position: absolute;
  inset: 0;
  pointer-events: none;
  background: linear-gradient(180deg, transparent 0%, rgba(0,229,255,0.04) 50%, transparent 100%);
  background-size: 100% 8px;
  animation: scan 8s linear infinite;
  z-index: 0;
}
@keyframes scan {
  0%   { background-position: 0 0; }
  100% { background-position: 0 100vh; }
}
.grid-bg {
  position: absolute;
  inset: 0;
  pointer-events: none;
  background-image:
    linear-gradient(rgba(138,99,255,0.04) 1px, transparent 1px),
    linear-gradient(90deg, rgba(138,99,255,0.04) 1px, transparent 1px);
  background-size: 40px 40px;
  z-index: 0;
}
.dashboard > * { position: relative; z-index: 1; }

/* —— 顶部状态条 —— */
.screen-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 4px;
}
.screen-head__left { display: flex; align-items: center; gap: 12px; }
.live-dot {
  width: 10px; height: 10px;
  border-radius: 50%;
  background: var(--danger);
  box-shadow: 0 0 8px var(--danger);
  animation: live-pulse 1.4s ease-in-out infinite;
}
@keyframes live-pulse {
  0%, 100% { opacity: 1; transform: scale(1); }
  50%      { opacity: 0.5; transform: scale(0.85); }
}
.screen-head__title {
  margin: 0;
  font-family: var(--font-display);
  font-size: 22px;
  font-weight: 700;
  color: var(--text-strong);
  letter-spacing: 0.08em;
}
.screen-head__badge {
  padding: 2px 8px;
  border-radius: 4px;
  background: var(--danger);
  color: #fff;
  font-family: var(--font-num);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.1em;
}
.screen-head__right { display: flex; align-items: center; gap: 16px; font-size: 12px; color: var(--text-muted); }
.heartbeat { display: inline-flex; align-items: center; gap: 6px; color: var(--success); }
.heartbeat__pulse {
  width: 8px; height: 8px; border-radius: 50%;
  background: var(--success);
  box-shadow: 0 0 6px var(--success);
  animation: hb 1.2s ease-in-out infinite;
}
@keyframes hb {
  0%, 100% { transform: scale(1); }
  20%      { transform: scale(1.4); }
  40%      { transform: scale(0.9); }
  60%      { transform: scale(1.2); }
}
.screen-head__time { font-family: var(--font-num); font-size: 16px; font-weight: 700; color: var(--text-strong); letter-spacing: 0.06em; }
.screen-head__updated { color: var(--text-dim); font-size: 11px; }

/* —— KPI 大屏 —— */
.kpi-row {
  display: grid;
  grid-template-columns: repeat(3, 1fr) 1.4fr;
  gap: 12px;
}
@media (max-width: 1100px) { .kpi-row { grid-template-columns: repeat(2, 1fr); } }
.kpi {
  padding: 16px 18px;
  border-radius: var(--radius);
  border: 1px solid var(--border-faint);
  background: var(--bg-card);
  display: flex;
  flex-direction: column;
  gap: 8px;
  position: relative;
  overflow: hidden;
}
.kpi::before {
  content: '';
  position: absolute;
  top: 0; left: 0; right: 0;
  height: 2px;
  background: linear-gradient(90deg, transparent, var(--neon-cyan), transparent);
  opacity: 0.6;
}
.kpi--online::before  { background: linear-gradient(90deg, transparent, var(--neon-cyan), transparent); }
.kpi--orders::before  { background: linear-gradient(90deg, transparent, var(--neon-purple), transparent); }
.kpi--revenue::before { background: linear-gradient(90deg, transparent, var(--warning), transparent); }
.kpi--peak::before    { background: linear-gradient(90deg, transparent, var(--danger), transparent); }
.kpi--qps::before     { background: linear-gradient(90deg, transparent, var(--danger), transparent); }
.kpi--peak { border-color: rgba(255,84,112,0.3); box-shadow: 0 0 16px rgba(255,84,112,0.08); }
.kpi__head { display: flex; align-items: center; gap: 8px; }
.kpi__icon { font-size: 16px; }
.kpi__label { font-size: 12px; color: var(--text-muted); letter-spacing: 0.04em; }
.kpi__value {
  font-family: var(--font-num);
  font-size: 30px;
  font-weight: 700;
  color: var(--text-strong);
  display: flex;
  align-items: baseline;
  gap: 2px;
  line-height: 1;
}
.kpi__prefix { font-size: 20px; color: var(--warning); margin-right: 2px; }
.kpi__delta { font-size: 11px; display: flex; align-items: center; gap: 6px; }
.kpi__delta.up { color: var(--success); }
.kpi__delta.down { color: var(--danger); }
.kpi__hint { color: var(--text-dim); font-size: 10px; }

/* —— QPS 折线 —— */
.kpi--qps { padding-bottom: 12px; }
.qps__now {
  margin-left: auto;
  font-size: 18px;
  font-weight: 700;
  color: var(--danger);
}
.qps-chart { position: relative; margin-top: 4px; }
.qps-chart svg { width: 100%; height: 80px; display: block; }
.qps-chart__grid line { stroke: rgba(138,99,255,0.08); stroke-width: 1; }
.qps-chart__line {
  fill: none;
  stroke: var(--danger);
  stroke-width: 1.8;
  stroke-linejoin: round;
  filter: drop-shadow(0 0 4px rgba(255,84,112,0.5));
}
.qps-chart__dot { fill: var(--danger); filter: drop-shadow(0 0 6px var(--danger)); }
.qps-chart__axis {
  position: absolute;
  right: 4px;
  top: 0;
  bottom: 0;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  font-size: 10px;
  color: var(--text-dim);
  font-family: var(--font-num);
}

/* —— 双栏 —— */
.dual {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}
@media (max-width: 1100px) { .dual { grid-template-columns: 1fr; } }

.panel {
  border-radius: var(--radius);
  padding: 16px 18px;
  border: 1px solid var(--border-faint);
  background: var(--bg-card);
}
.panel__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}
.panel__head h3 {
  margin: 0;
  font-family: var(--font-display);
  font-size: 15px;
  color: var(--text-strong);
  letter-spacing: 0.06em;
}
.panel__hint { font-size: 11px; color: var(--text-dim); }

/* —— Top10 排行榜（FLIP） —— */
.rank-list {
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 420px;
  overflow-y: auto;
}
.rank-row {
  display: grid;
  grid-template-columns: 28px 32px 1fr 1fr 70px 36px;
  gap: 10px;
  align-items: center;
  padding: 8px 6px;
  border-radius: 6px;
  background: rgba(7,8,26,0.4);
  transition: background 0.2s;
}
.rank-row:hover { background: rgba(138,99,255,0.08); }
.rank-row.is-top { background: rgba(255,203,85,0.06); }
.rank-row__no {
  width: 24px; height: 24px;
  display: grid; place-items: center;
  border-radius: 50%;
  font-family: var(--font-num);
  font-size: 12px;
  font-weight: 700;
  background: rgba(86,88,122,0.2);
  color: var(--text-muted);
}
.no--1 { background: linear-gradient(135deg,#ffd666,#ff8a00); color: #07081a; box-shadow: 0 0 10px rgba(255,214,102,0.5); }
.no--2 { background: linear-gradient(135deg,#c9cdd4,#8a8fb5); color: #07081a; }
.no--3 { background: linear-gradient(135deg,#ffa572,#ff7ad9); color: #07081a; }
.rank-row__avatar {
  width: 32px; height: 32px;
  border-radius: 8px;
  display: grid; place-items: center;
  font-family: var(--font-display);
  font-weight: 700;
  color: #07081a;
  font-size: 14px;
}
.rank-row__info { display: flex; flex-direction: column; min-width: 0; }
.rank-row__name { font-size: 13px; color: var(--text-strong); font-weight: 600; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.rank-row__room { font-size: 11px; color: var(--text-muted); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.rank-row__bar { height: 6px; border-radius: 3px; background: rgba(7,8,26,0.6); overflow: hidden; }
.rank-row__bar-fill { height: 100%; background: linear-gradient(90deg, var(--neon-purple), var(--neon-cyan)); border-radius: 3px; transition: width 0.5s cubic-bezier(0.16, 1, 0.3, 1); }
.rank-row__score { font-family: var(--font-num); font-weight: 700; color: var(--text-strong); text-align: right; font-size: 13px; }
.rank-row__delta { font-size: 10px; text-align: right; }
.rank-row__delta.up { color: var(--success); }
.rank-row__delta.down { color: var(--danger); }

/* FLIP 动画 */
.rank-move,
.rank-enter-active,
.rank-leave-active {
  transition: all 0.5s cubic-bezier(0.16, 1, 0.3, 1);
}
.rank-enter-from { opacity: 0; transform: translateX(-20px); }
.rank-leave-to { opacity: 0; transform: translateX(20px); }
.rank-leave-active { position: absolute; }

/* —— 秒杀战况 —— */
.sk-list { list-style: none; padding: 0; margin: 0; display: flex; flex-direction: column; gap: 12px; }
.sk-row {
  padding: 10px 12px;
  border-radius: 6px;
  background: rgba(7,8,26,0.4);
  border-left: 3px solid var(--neon-purple);
  transition: all 0.2s;
}
.sk-row.is-critical {
  border-left-color: var(--danger);
  background: rgba(255,84,112,0.06);
  animation: critical-pulse 1.6s ease-in-out infinite;
}
@keyframes critical-pulse {
  0%, 100% { box-shadow: 0 0 0 rgba(255,84,112,0); }
  50%      { box-shadow: 0 0 12px rgba(255,84,112,0.2); }
}
.sk-row__head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 6px; }
.sk-row__name { font-size: 13px; color: var(--text-strong); font-weight: 600; }
.sk-row__anchor { font-size: 11px; color: var(--neon-cyan); }
.sk-row__bar {
  position: relative;
  height: 22px;
  border-radius: 4px;
  background: rgba(7,8,26,0.6);
  overflow: hidden;
}
.sk-row__bar-fill {
  height: 100%;
  background: linear-gradient(90deg, var(--neon-purple), var(--neon-cyan));
  border-radius: 4px;
  transition: width 0.6s cubic-bezier(0.16, 1, 0.3, 1);
}
.sk-row.is-critical .sk-row__bar-fill {
  background: linear-gradient(90deg, var(--danger), var(--warning));
}
.sk-row__bar-text {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  padding: 0 8px;
  font-family: var(--font-num);
  font-size: 11px;
  color: var(--text-strong);
  font-weight: 600;
}
.sk-row__meta { display: flex; justify-content: space-between; margin-top: 6px; font-size: 11px; }
.sk-row__pct { color: var(--text-strong); font-weight: 700; }
.sk-row.is-critical .sk-row__pct { color: var(--danger); }
.sk-row__qps { color: var(--warning); }
.sk-row__revenue { color: var(--success); }

/* —— 柱状图 —— */
.bar-chart {
  display: flex;
  align-items: flex-end;
  gap: 10px;
  height: 220px;
  padding: 12px 4px 0;
  overflow-x: auto;
}
.bar-col {
  flex: 1;
  min-width: 60px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  height: 100%;
}
.bar-col__value {
  font-family: var(--font-num);
  font-size: 12px;
  font-weight: 700;
  color: var(--text-strong);
}
.bar-col__bar {
  flex: 1;
  width: 100%;
  max-width: 48px;
  display: flex;
  align-items: flex-end;
  border-radius: 4px 4px 0 0;
  background: rgba(7,8,26,0.4);
  overflow: hidden;
}
.bar-col__fill {
  width: 100%;
  border-radius: 4px 4px 0 0;
  transition: height 0.6s cubic-bezier(0.16, 1, 0.3, 1);
  box-shadow: 0 0 12px rgba(138,99,255,0.2);
}
.bar-col__label {
  font-size: 11px;
  color: var(--text-muted);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 80px;
}

/* —— 订单流 —— */
.feed-list { list-style: none; padding: 0; margin: 0; display: flex; flex-direction: column; gap: 4px; max-height: 320px; overflow-y: auto; }
.feed-row {
  display: grid;
  grid-template-columns: 70px 90px 1fr 70px 60px;
  gap: 8px;
  align-items: center;
  padding: 7px 10px;
  border-radius: 5px;
  background: rgba(7,8,26,0.4);
  font-size: 12px;
}
.feed-row--paid { border-left: 2px solid var(--success); }
.feed-row--pending { border-left: 2px solid var(--warning); }
.feed-row--cancel { border-left: 2px solid var(--text-dim); opacity: 0.7; }
.feed-row__time { color: var(--text-dim); font-size: 11px; }
.feed-row__user { color: var(--neon-cyan); }
.feed-row__product { color: var(--text); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.feed-row__amt { font-family: var(--font-num); color: var(--warning); font-weight: 600; text-align: right; }
.feed-row__status { font-size: 10px; text-align: right; font-weight: 600; }
.st--paid { color: var(--success); }
.st--pending { color: var(--warning); }
.st--cancel { color: var(--text-dim); }

/* 订单流进场动画 */
.feed-enter-active { transition: all 0.4s cubic-bezier(0.16, 1, 0.3, 1); }
.feed-leave-active { transition: all 0.3s ease; }
.feed-enter-from { opacity: 0; transform: translateX(-20px); background: rgba(0,229,255,0.12); }
.feed-leave-to { opacity: 0; transform: translateX(20px); }
.feed-move { transition: transform 0.4s cubic-bezier(0.16, 1, 0.3, 1); }

/* —— 系统健康度 —— */
.health-list { list-style: none; padding: 0; margin: 0; display: flex; flex-direction: column; gap: 6px; }
.health-row {
  display: grid;
  grid-template-columns: 12px 1fr auto 60px;
  gap: 12px;
  align-items: center;
  padding: 8px 10px;
  border-radius: 5px;
  background: rgba(7,8,26,0.4);
}
.health-row__dot {
  width: 8px; height: 8px;
  border-radius: 50%;
}
.health--ok .health-row__dot { background: var(--success); box-shadow: 0 0 6px var(--success); }
.health--warn .health-row__dot { background: var(--warning); box-shadow: 0 0 6px var(--warning); animation: warn-blink 1.2s ease-in-out infinite; }
.health--down .health-row__dot { background: var(--danger); box-shadow: 0 0 6px var(--danger); animation: warn-blink 0.8s ease-in-out infinite; }
@keyframes warn-blink {
  0%, 100% { opacity: 1; }
  50%      { opacity: 0.3; }
}
.health-row__body { display: flex; flex-direction: column; min-width: 0; }
.health-row__name { font-family: var(--font-num); font-size: 12px; color: var(--text-strong); font-weight: 600; }
.health-row__desc { font-size: 10px; color: var(--text-muted); }
.health-row__metrics { display: flex; gap: 8px; font-size: 11px; }
.health-row__cpu { color: var(--text); }
.health-row__mem { color: var(--text-muted); }
.health-row__rt { color: var(--neon-cyan); }
.health-row__status { font-size: 11px; font-weight: 600; text-align: right; }
.hs--ok { color: var(--success); }
.hs--warn { color: var(--warning); }
.hs--down { color: var(--danger); }

.health-foot {
  display: flex;
  gap: 16px;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid var(--border-faint);
  font-size: 12px;
}
.health-foot__item { display: inline-flex; align-items: center; gap: 6px; color: var(--text-muted); }
.health-foot__item i {
  width: 8px; height: 8px;
  border-radius: 50%;
  display: inline-block;
}
.health-foot__item.ok i { background: var(--success); }
.health-foot__item.warn i { background: var(--warning); }
.health-foot__item.down i { background: var(--danger); }

/* —— 滚动条美化 —— */
.rank-list::-webkit-scrollbar,
.feed-list::-webkit-scrollbar { width: 4px; }
.rank-list::-webkit-scrollbar-thumb,
.feed-list::-webkit-scrollbar-thumb { background: rgba(138,99,255,0.3); border-radius: 2px; }

.num { font-family: var(--font-num); }
</style>
