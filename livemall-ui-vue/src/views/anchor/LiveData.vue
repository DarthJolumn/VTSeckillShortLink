<template>
  <div class="live-data">
    <header class="page-head">
      <div>
        <h1 class="page-head__title">直播数据</h1>
        <p class="page-head__sub">最近 7 场直播复盘 · 在线 / 弹幕 / 礼物 / 转化</p>
      </div>
      <div class="range">
        <button v-for="r in RANGES" :key="r.key"
          class="range__btn" :class="{ 'is-on': range === r.key }"
          @click="range = r.key">{{ r.label }}</button>
      </div>
    </header>

    <!-- 顶部 KPI -->
    <div class="kpi-row">
      <div v-for="k in kpis" :key="k.label" class="kpi glass">
        <span class="kpi__label">{{ k.label }}</span>
        <span class="kpi__value"><NumberFlip :value="k.value" /></span>
        <span class="kpi__delta" :class="k.delta >= 0 ? 'up' : 'down'">
          {{ k.delta >= 0 ? '▲' : '▼' }} {{ Math.abs(k.delta).toFixed(1) }}%
        </span>
        <span class="kpi__spark"><Sparkline :points="k.spark" :color="k.color" /></span>
      </div>
    </div>

    <!-- 主曲线 -->
    <div class="panel glass">
      <div class="panel__head">
        <h3>在线人数曲线</h3>
        <div class="legend">
          <span class="legend__item"><i class="dot dot--cyan" /> 在线</span>
          <span class="legend__item"><i class="dot dot--purple" /> 弹幕</span>
        </div>
      </div>
      <div class="chart">
        <svg :viewBox="`0 0 ${CHART_W} ${CHART_H}`" preserveAspectRatio="none">
          <!-- 网格 -->
          <g class="chart__grid">
            <line v-for="i in 5" :key="'h'+i" :x1="0" :x2="CHART_W" :y1="(i-1)*CHART_H/4" :y2="(i-1)*CHART_H/4" />
            <line v-for="i in 7" :key="'v'+i" :x1="(i-1)*CHART_W/6" :x2="(i-1)*CHART_W/6" :y1="0" :y2="CHART_H" />
          </g>
          <!-- 在线曲线 -->
          <path :d="onlinePath" class="chart__line chart__line--cyan" />
          <path :d="onlineAreaPath" class="chart__area chart__area--cyan" />
          <!-- 弹幕曲线 -->
          <path :d="barragePath" class="chart__line chart__line--purple" />
          <!-- 数据点 -->
          <g>
            <circle v-for="(p, i) in onlinePoints" :key="'o'+i" :cx="p.x" :cy="p.y" r="3" class="chart__dot chart__dot--cyan" />
          </g>
        </svg>
        <div class="chart__x">
          <span v-for="(label, i) in xLabels" :key="i">{{ label }}</span>
        </div>
      </div>
    </div>

    <!-- 双栏 -->
    <div class="dual">
      <!-- 礼物收入分布 -->
      <div class="panel glass">
        <div class="panel__head">
          <h3>礼物收入分布</h3>
          <span class="panel__hint">合计 ¥{{ giftTotal.toFixed(0) }}</span>
        </div>
        <ul class="gift-list">
          <li v-for="g in giftBreakdown" :key="g.name">
            <span class="gift-list__icon">{{ g.icon }}</span>
            <span class="gift-list__name">{{ g.name }}</span>
            <div class="gift-list__bar">
              <div class="gift-list__bar-fill" :style="{ width: g.pct + '%', background: g.color }" />
            </div>
            <span class="gift-list__count">×{{ g.count }}</span>
            <span class="gift-list__amt">¥{{ g.amount }}</span>
          </li>
        </ul>
      </div>

      <!-- 转化漏斗 -->
      <div class="panel glass">
        <div class="panel__head">
          <h3>转化漏斗</h3>
          <span class="panel__hint">浏览 → 下单</span>
        </div>
        <div class="funnel">
          <div v-for="(f, i) in funnel" :key="f.label" class="funnel__step" :style="{ '--c': f.color }">
            <div class="funnel__bar" :style="{ width: f.pct + '%' }">
              <span class="funnel__label">{{ f.label }}</span>
              <span class="funnel__value">{{ f.value.toLocaleString() }}</span>
            </div>
            <span class="funnel__pct">{{ f.pct.toFixed(1) }}%</span>
            <span v-if="i < funnel.length - 1" class="funnel__drop">
              ↓ 转化 {{ ((funnel[i+1].value / f.value) * 100).toFixed(1) }}%
            </span>
          </div>
        </div>
      </div>
    </div>

    <!-- 历史场次 -->
    <div class="panel glass">
      <div class="panel__head">
        <h3>历史直播场次</h3>
        <span class="panel__hint">最近 7 场</span>
      </div>
      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>日期</th>
              <th>标题</th>
              <th>时长</th>
              <th>峰值在线</th>
              <th>累计观看</th>
              <th>弹幕</th>
              <th>礼物</th>
              <th>收益</th>
              <th>订单</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(s, i) in sessions" :key="i">
              <td class="num">{{ s.date }}</td>
              <td>{{ s.title }}</td>
              <td class="num">{{ s.duration }}</td>
              <td class="num">{{ s.peak }}</td>
              <td class="num">{{ s.totalView }}</td>
              <td class="num">{{ s.barrage }}</td>
              <td class="num">{{ s.gift }}</td>
              <td class="num revenue">¥{{ s.revenue }}</td>
              <td class="num">{{ s.orders }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, ref, h } from 'vue'
import NumberFlip from '@/components/base/NumberFlip.vue'

const RANGES = [
  { key: '7d', label: '近 7 天' },
  { key: '30d', label: '近 30 天' },
  { key: '90d', label: '近 90 天' },
]
const range = ref('7d')

const CHART_W = 800
const CHART_H = 240

// —— 在线曲线数据（7 天，每天 6 个采样点） ——
const onlineSeries = [120, 180, 240, 380, 520, 680, 540, 460, 380, 420, 580, 720, 880, 940, 760, 620, 540, 480, 420, 360, 320, 280, 240, 200, 180, 160, 140, 120]
const barrageSeries = [40, 60, 80, 120, 180, 240, 200, 160, 140, 160, 220, 280, 340, 360, 300, 240, 200, 160, 140, 120, 100, 80, 70, 60, 50, 45, 40, 35]

const xLabels = ['00', '04', '08', '12', '16', '20', '24']

function buildPath(series, max) {
  const step = CHART_W / (series.length - 1)
  return series.map((v, i) => {
    const x = i * step
    const y = CHART_H - (v / max) * CHART_H * 0.92 - 6
    return `${i === 0 ? 'M' : 'L'}${x.toFixed(1)},${y.toFixed(1)}`
  }).join(' ')
}
function buildArea(series, max) {
  const step = CHART_W / (series.length - 1)
  const line = series.map((v, i) => {
    const x = i * step
    const y = CHART_H - (v / max) * CHART_H * 0.92 - 6
    return `${i === 0 ? 'M' : 'L'}${x.toFixed(1)},${y.toFixed(1)}`
  }).join(' ')
  return `${line} L${CHART_W},${CHART_H} L0,${CHART_H} Z`
}

const onlineMax = Math.max(...onlineSeries) * 1.1
const barrageMax = Math.max(...barrageSeries) * 1.1
const onlinePath = computed(() => buildPath(onlineSeries, onlineMax))
const onlineAreaPath = computed(() => buildArea(onlineSeries, onlineMax))
const barragePath = computed(() => buildPath(barrageSeries, barrageMax))
const onlinePoints = computed(() => {
  const step = CHART_W / (onlineSeries.length - 1)
  return onlineSeries.map((v, i) => ({
    x: i * step,
    y: CHART_H - (v / onlineMax) * CHART_H * 0.92 - 6,
  }))
})

// —— KPI ——
const kpis = computed(() => {
  const totalView = onlineSeries.reduce((a, b) => a + b, 0)
  const avgOnline = Math.round(onlineSeries.reduce((a, b) => a + b, 0) / onlineSeries.length)
  const totalBarrage = barrageSeries.reduce((a, b) => a + b, 0)
  const revenue = giftBreakdown.value.reduce((a, g) => a + g.amount, 0)
  return [
    { label: '累计观看', value: totalView, delta: 12.4, color: '#00e5ff', spark: onlineSeries.slice(0, 12) },
    { label: '平均在线', value: avgOnline, delta: 8.2, color: '#8a63ff', spark: onlineSeries.slice(12) },
    { label: '弹幕总数', value: totalBarrage, delta: -3.6, color: '#ff7ad9', spark: barrageSeries },
    { label: '礼物收益', value: revenue, delta: 24.1, color: '#ffcb55', spark: [120, 180, 240, 200, 280, 340, 360, 300, 240, 200, 160, 140] },
  ]
})

// —— 礼物分布 ——
const giftBreakdown = ref([
  { name: '火箭', icon: '🚀', count: 12, amount: 7992, color: 'linear-gradient(90deg,#ff5470,#ff8a00)' },
  { name: '跑车', icon: '🏎️', count: 38, amount: 4560, color: 'linear-gradient(90deg,#8a63ff,#00e5ff)' },
  { name: '玫瑰', icon: '🌹', count: 156, amount: 1404, color: 'linear-gradient(90deg,#ff7ad9,#ff5470)' },
  { name: '点赞', icon: '👍', count: 412, amount: 0, color: 'linear-gradient(90deg,#52e5a4,#00e5ff)' },
])
const giftTotal = computed(() => giftBreakdown.value.reduce((a, g) => a + g.amount, 0))
// 计算占比
giftBreakdown.value.forEach(g => {
  g.pct = giftTotal.value > 0 ? (g.amount / giftTotal.value) * 100 : 0
})

// —— 漏斗 ——
const funnel = computed(() => {
  const total = 12480
  const view = total
  const click = Math.round(view * 0.42)
  const cart = Math.round(click * 0.35)
  const order = Math.round(cart * 0.62)
  const paid = Math.round(order * 0.88)
  return [
    { label: '曝光', value: view, pct: 100, color: '#00e5ff' },
    { label: '点击', value: click, pct: (click / view) * 100, color: '#4cc9f0' },
    { label: '加购', value: cart, pct: (cart / view) * 100, color: '#8a63ff' },
    { label: '下单', value: order, pct: (order / view) * 100, color: '#ff7ad9' },
    { label: '支付', value: paid, pct: (paid / view) * 100, color: '#ff5470' },
  ]
})

// —— 历史场次 ——
const sessions = ref([
  { date: '06-27', title: '深夜数码秒杀局', duration: '2h12m', peak: 940, totalView: 12480, barrage: 3260, gift: 156, revenue: 18420, orders: 412 },
  { date: '06-26', title: '美妆最后一小时', duration: '1h08m', peak: 720, totalView: 8640, barrage: 2140, gift: 88, revenue: 9280, orders: 286 },
  { date: '06-25', title: '零食清仓大放送', duration: '3h24m', peak: 1180, totalView: 18620, barrage: 4520, gift: 224, revenue: 26840, orders: 682 },
  { date: '06-24', title: '潮鞋限量首发', duration: '1h30m', peak: 1560, totalView: 16480, barrage: 3680, gift: 198, revenue: 32140, orders: 524 },
  { date: '06-23', title: '夏季服饰专场', duration: '2h00m', peak: 680, totalView: 7240, barrage: 1860, gift: 64, revenue: 6420, orders: 198 },
  { date: '06-22', title: '智能家电精选', duration: '1h45m', peak: 820, totalView: 9820, barrage: 2480, gift: 112, revenue: 12480, orders: 312 },
  { date: '06-21', title: '深夜数码秒杀局', duration: '2h30m', peak: 1020, totalView: 13280, barrage: 3420, gift: 168, revenue: 19260, orders: 448 },
])

// —— Sparkline 内联组件 ——
const Sparkline = {
  props: { points: Array, color: String },
  setup(props) {
    const W = 80, H = 24
    const path = computed(() => {
      if (!props.points?.length) return ''
      const max = Math.max(...props.points)
      const min = Math.min(...props.points)
      const range = max - min || 1
      const step = W / (props.points.length - 1)
      return props.points.map((v, i) => {
        const x = i * step
        const y = H - ((v - min) / range) * H * 0.85 - 2
        return `${i === 0 ? 'M' : 'L'}${x.toFixed(1)},${y.toFixed(1)}`
      }).join(' ')
    })
    return () => h('svg', { viewBox: `0 0 ${W} ${H}`, preserveAspectRatio: 'none' }, [
      h('path', { d: path.value, fill: 'none', stroke: props.color, 'stroke-width': 1.5 }),
    ])
  },
}
</script>

<style scoped>
.live-data { display: flex; flex-direction: column; gap: 16px; }

.page-head { display: flex; align-items: flex-end; justify-content: space-between; gap: 16px; }
.page-head__title { margin: 0; font-family: var(--font-display); font-size: 24px; color: var(--text-strong); letter-spacing: 0.04em; }
.page-head__sub { margin: 6px 0 0; font-size: 13px; color: var(--text-muted); }
.range { display: flex; gap: 4px; padding: 4px; border-radius: 999px; border: 1px solid var(--border-faint); background: var(--bg-card); }
.range__btn { padding: 6px 14px; border-radius: 999px; border: none; background: transparent; color: var(--text-muted); font-size: 12px; cursor: pointer; transition: all 0.2s; }
.range__btn:hover { color: var(--text); }
.range__btn.is-on { background: var(--neon-purple); color: #fff; }

/* —— KPI —— */
.kpi-row { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; }
@media (max-width: 900px) { .kpi-row { grid-template-columns: repeat(2, 1fr); } }
.kpi {
  padding: 16px 18px;
  border-radius: var(--radius);
  border: 1px solid var(--border-faint);
  background: var(--bg-card);
  display: flex; flex-direction: column; gap: 4px;
  position: relative;
}
.kpi__label { font-size: 12px; color: var(--text-muted); }
.kpi__value { font-family: var(--font-num); font-size: 26px; font-weight: 700; color: var(--text-strong); }
.kpi__delta { font-size: 11px; }
.kpi__delta.up { color: var(--success); }
.kpi__delta.down { color: var(--danger); }
.kpi__spark { position: absolute; right: 14px; bottom: 14px; opacity: 0.7; }
.kpi__spark svg { width: 80px; height: 24px; display: block; }

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
.legend { display: flex; gap: 14px; }
.legend__item { display: flex; align-items: center; gap: 6px; font-size: 12px; color: var(--text-muted); }
.dot { width: 8px; height: 8px; border-radius: 50%; display: inline-block; }
.dot--cyan { background: var(--neon-cyan); box-shadow: 0 0 6px var(--neon-cyan-soft); }
.dot--purple { background: var(--neon-purple); box-shadow: 0 0 6px var(--neon-purple-soft); }

/* —— 图表 —— */
.chart { position: relative; }
.chart svg { width: 100%; height: 240px; display: block; }
.chart__grid line { stroke: rgba(138,99,255,0.08); stroke-width: 1; }
.chart__line { fill: none; stroke-width: 2; stroke-linejoin: round; stroke-linecap: round; }
.chart__line--cyan { stroke: var(--neon-cyan); filter: drop-shadow(0 0 4px var(--neon-cyan-soft)); }
.chart__line--purple { stroke: var(--neon-purple); filter: drop-shadow(0 0 4px var(--neon-purple-soft)); }
.chart__area { opacity: 0.18; }
.chart__area--cyan { fill: var(--neon-cyan); }
.chart__dot { fill: var(--neon-cyan); }
.chart__dot--cyan { fill: var(--neon-cyan); }
.chart__x {
  display: flex; justify-content: space-between;
  margin-top: 6px;
  font-family: var(--font-num);
  font-size: 11px;
  color: var(--text-dim);
}

/* —— 双栏 —— */
.dual { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
@media (max-width: 900px) { .dual { grid-template-columns: 1fr; } }

/* —— 礼物分布 —— */
.gift-list { list-style: none; padding: 0; margin: 0; display: flex; flex-direction: column; gap: 10px; }
.gift-list li {
  display: grid;
  grid-template-columns: 24px 60px 1fr 60px 70px;
  gap: 10px;
  align-items: center;
  font-size: 12px;
}
.gift-list__icon { font-size: 16px; text-align: center; }
.gift-list__name { color: var(--text); }
.gift-list__bar { height: 8px; border-radius: 4px; background: rgba(7,8,26,0.6); overflow: hidden; }
.gift-list__bar-fill { height: 100%; border-radius: 4px; transition: width 0.4s; }
.gift-list__count { color: var(--text-muted); text-align: right; }
.gift-list__amt { color: var(--warning); font-family: var(--font-num); text-align: right; }

/* —— 漏斗 —— */
.funnel { display: flex; flex-direction: column; gap: 10px; }
.funnel__step {
  position: relative;
  display: flex; align-items: center; gap: 12px;
}
.funnel__bar {
  height: 32px;
  border-radius: 6px;
  background: var(--c);
  display: flex; align-items: center; justify-content: space-between;
  padding: 0 12px;
  color: #07081a;
  font-size: 12px;
  font-weight: 600;
  transition: width 0.4s;
  min-width: 80px;
  box-shadow: 0 0 12px color-mix(in srgb, var(--c) 40%, transparent);
}
.funnel__label { font-family: var(--font-display); }
.funnel__value { font-family: var(--font-num); }
.funnel__pct {
  position: absolute; left: calc(var(--w, 100%) + 8px);
  font-family: var(--font-num);
  font-size: 12px;
  color: var(--text-muted);
}
.funnel__step .funnel__pct { position: static; min-width: 50px; }
.funnel__drop {
  margin-left: auto;
  font-size: 11px;
  color: var(--text-dim);
  font-family: var(--font-num);
}

/* —— 历史表 —— */
.table-wrap { overflow-x: auto; }
table { width: 100%; border-collapse: collapse; min-width: 720px; }
th, td { padding: 10px 12px; text-align: left; font-size: 13px; border-bottom: 1px solid var(--border-faint); }
th { color: var(--text-muted); font-weight: 500; font-size: 12px; letter-spacing: 0.06em; background: rgba(7,8,26,0.4); }
tbody tr:last-child td { border-bottom: none; }
tbody tr:hover { background: rgba(138,99,255,0.04); }
.num { font-family: var(--font-num); color: var(--text); }
.revenue { color: var(--warning); font-weight: 600; }
</style>
