<template>
  <div class="act-overview">
    <header class="page-head">
      <div>
        <h1 class="page-head__title">活动总览</h1>
        <p class="page-head__sub">全平台秒杀活动监控 · 状态分布 / 主播排行 / 异常预警</p>
      </div>
      <div class="range">
        <button v-for="r in RANGES" :key="r.key"
          class="range__btn" :class="{ 'is-on': range === r.key }"
          @click="range = r.key">{{ r.label }}</button>
      </div>
    </header>

    <!-- KPI -->
    <div class="kpi-row">
      <div v-for="k in kpis" :key="k.label" class="kpi glass" :class="`kpi--${k.key}`">
        <span class="kpi__icon">{{ k.icon }}</span>
        <div class="kpi__main">
          <span class="kpi__value"><NumberFlip :value="k.value" /></span>
          <span class="kpi__label">{{ k.label }}</span>
        </div>
        <span class="kpi__delta" :class="k.delta >= 0 ? 'up' : 'down'">
          {{ k.delta >= 0 ? '▲' : '▼' }} {{ Math.abs(k.delta) }}%
        </span>
      </div>
    </div>

    <!-- 状态分布 + 主播排行 -->
    <div class="dual">
      <div class="panel glass">
        <div class="panel__head">
          <h3>活动状态分布</h3>
          <span class="panel__hint">合计 {{ totalActivities }} 场</span>
        </div>
        <div class="donut-wrap">
          <svg :viewBox="`0 0 200 200`" class="donut">
            <circle v-for="(s, i) in donutSegments" :key="i"
              :cx="100" :cy="100" :r="70" fill="none"
              :stroke="s.color" :stroke-width="22"
              :stroke-dasharray="`${s.len} ${circumference - s.len}`"
              :stroke-dashoffset="-s.offset"
              :style="{ transition: 'stroke-dasharray 0.6s, stroke-dashoffset 0.6s' }"
            />
            <text x="100" y="95" text-anchor="middle" class="donut__num">{{ totalActivities }}</text>
            <text x="100" y="115" text-anchor="middle" class="donut__label">活动总数</text>
          </svg>
          <ul class="donut-legend">
            <li v-for="(s, i) in donutSegments" :key="i">
              <span class="donut-legend__dot" :style="{ background: s.color }" />
              <span class="donut-legend__label">{{ s.label }}</span>
              <span class="donut-legend__count">{{ s.count }}</span>
              <span class="donut-legend__pct">{{ s.pct.toFixed(1) }}%</span>
            </li>
          </ul>
        </div>
      </div>

      <div class="panel glass">
        <div class="panel__head">
          <h3>主播活动排行</h3>
          <span class="panel__hint">按订单数</span>
        </div>
        <ol class="anchor-rank">
          <li v-for="(a, i) in anchorRank" :key="a.name" class="anchor-rank__item">
            <span class="anchor-rank__no" :class="`no--${i < 3 ? i + 1 : 'other'}`">{{ i + 1 }}</span>
            <div class="anchor-rank__avatar" :style="{ background: a.avatarBg }">{{ a.name[0] }}</div>
            <div class="anchor-rank__info">
              <span class="anchor-rank__name">{{ a.name }}</span>
              <span class="anchor-rank__meta">{{ a.activities }} 场 · ¥{{ a.revenue.toLocaleString() }}</span>
            </div>
            <div class="anchor-rank__bar">
              <div class="anchor-rank__bar-fill" :style="{ width: (a.orders / maxOrders * 100) + '%' }" />
            </div>
            <span class="anchor-rank__orders num">{{ a.orders }}</span>
          </li>
        </ol>
      </div>
    </div>

    <!-- 全部活动表 -->
    <div class="panel glass">
      <div class="panel__head">
        <h3>全部活动</h3>
        <div class="filters">
          <button v-for="f in statusFilters" :key="f.key"
            class="filter" :class="{ 'is-on': activeStatus === f.key }"
            @click="activeStatus = f.key">
            {{ f.label }} <span class="filter__count">{{ f.count }}</span>
          </button>
          <input v-model.trim="keyword" type="text" placeholder="搜索活动 / 主播…" />
        </div>
      </div>
      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>活动 ID</th>
              <th>名称</th>
              <th>主播</th>
              <th>秒杀价</th>
              <th>库存</th>
              <th>已售</th>
              <th>售罄率</th>
              <th>状态</th>
              <th>开始时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="a in filtered" :key="a.id">
              <td class="num">{{ a.id }}</td>
              <td>{{ a.name }}</td>
              <td>{{ a.anchor }}</td>
              <td class="num">¥{{ a.price }}</td>
              <td class="num">{{ a.stockTotal }}</td>
              <td class="num">{{ a.sold }}</td>
              <td>
                <div class="sell-rate">
                  <div class="sell-rate__bar">
                    <div class="sell-rate__fill" :style="{ width: sellPct(a) + '%' }" />
                  </div>
                  <span class="sell-rate__num">{{ sellPct(a).toFixed(0) }}%</span>
                </div>
              </td>
              <td><span class="status-tag" :class="`status-tag--${statusKey(a.status)}`">{{ statusLabel(a.status) }}</span></td>
              <td class="num">{{ formatTime(a.startAt) }}</td>
              <td>
                <button class="op op--view" @click="onView(a)">详情</button>
                <button v-if="a.status === 1" class="op op--stop" @click="onForceStop(a)">强制下架</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- 异常预警 -->
    <div class="panel glass alert-panel">
      <div class="panel__head">
        <h3>⚠️ 异常预警</h3>
        <span class="panel__hint">{{ alerts.length }} 条</span>
      </div>
      <ul class="alert-list">
        <li v-for="(al, i) in alerts" :key="i" class="alert" :class="`alert--${al.level}`">
          <span class="alert__icon">{{ al.icon }}</span>
          <div class="alert__body">
            <span class="alert__title">{{ al.title }}</span>
            <span class="alert__desc">{{ al.desc }}</span>
          </div>
          <span class="alert__time num">{{ formatTime(al.at) }}</span>
        </li>
      </ul>
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { showToast } from '@/utils/toast'
import NumberFlip from '@/components/base/NumberFlip.vue'

const RANGES = [
  { key: 'today', label: '今日' },
  { key: '7d', label: '近 7 天' },
  { key: '30d', label: '近 30 天' },
]
const range = ref('7d')

// —— mock 活动数据 ——
const allActivities = ref([
  { id: 'A001', name: '旗舰降噪耳机 · 限量500台', anchor: 'NeonAnchor', price: 299, stock: 480, stockTotal: 500, sold: 20, status: 1, startAt: Date.now() - 5 * 60000 },
  { id: 'A002', name: '机械键盘 RGB · 200台', anchor: 'GlowQueen', price: 199, stock: 200, stockTotal: 200, sold: 0, status: 0, startAt: Date.now() + 30 * 60000 },
  { id: 'A003', name: '4K 显示器 · 50台', anchor: 'SneakerX', price: 1299, stock: 0, stockTotal: 50, sold: 50, status: 2, startAt: Date.now() - 2 * 3600000 },
  { id: 'A004', name: '蓝牙音箱 · 100台', anchor: 'SnackKing', price: 99, stock: 100, stockTotal: 100, sold: 0, status: 3, startAt: Date.now() - 24 * 3600000 },
  { id: 'A005', name: '智能手表 · 80台', anchor: 'NeonAnchor', price: 599, stock: 12, stockTotal: 80, sold: 68, status: 1, startAt: Date.now() - 12 * 60000 },
  { id: 'A006', name: '游戏鼠标 · 300台', anchor: 'GlowQueen', price: 149, stock: 280, stockTotal: 300, sold: 20, status: 1, startAt: Date.now() - 3 * 60000 },
  { id: 'A007', name: '电竞椅 · 30台', anchor: 'SneakerX', price: 899, stock: 0, stockTotal: 30, sold: 30, status: 2, startAt: Date.now() - 5 * 3600000 },
  { id: 'A008', name: '机械键盘客制化 · 50台', anchor: 'SnackKing', price: 1299, stock: 50, stockTotal: 50, sold: 0, status: 0, startAt: Date.now() + 2 * 3600000 },
])

const totalActivities = computed(() => allActivities.value.length)

// —— KPI ——
const kpis = computed(() => {
  const list = allActivities.value
  const running = list.filter(a => a.status === 1).length
  const ended = list.filter(a => a.status === 2).length
  const totalSold = list.reduce((s, a) => s + a.sold, 0)
  const totalRevenue = list.reduce((s, a) => s + a.sold * a.price, 0)
  return [
    { key: 'total',   icon: '📋', label: '活动总数', value: list.length, delta: 12 },
    { key: 'running', icon: '⚡', label: '进行中',   value: running, delta: 8 },
    { key: 'sold',    icon: '📦', label: '累计售出', value: totalSold, delta: 24 },
    { key: 'revenue', icon: '💰', label: '成交额',   value: totalRevenue, delta: 18 },
  ]
})

// —— 状态分布 donut ——
const circumference = 2 * Math.PI * 70
const donutSegments = computed(() => {
  const list = allActivities.value
  const groups = [
    { label: '待开始', status: 0, color: '#ffcb55' },
    { label: '进行中', status: 1, color: '#ff5470' },
    { label: '已结束', status: 2, color: '#8a63ff' },
    { label: '已取消', status: 3, color: '#56587a' },
  ]
  let offset = 0
  return groups.map(g => {
    const count = list.filter(a => a.status === g.status).length
    const pct = list.length ? count / list.length : 0
    const len = pct * circumference
    const seg = { ...g, count, pct: pct * 100, len, offset }
    offset += len
    return seg
  })
})

// —— 主播排行 ——
const anchorRank = computed(() => {
  const map = new Map()
  allActivities.value.forEach(a => {
    if (!map.has(a.anchor)) {
      map.set(a.anchor, { name: a.anchor, activities: 0, sold: 0, revenue: 0, orders: 0, avatarBg: '' })
    }
    const u = map.get(a.anchor)
    u.activities += 1
    u.sold += a.sold
    u.revenue += a.sold * a.price
    u.orders += a.sold
  })
  const COLORS = ['linear-gradient(135deg,#8a63ff,#00e5ff)', 'linear-gradient(135deg,#ff7ad9,#8a63ff)', 'linear-gradient(135deg,#52e5a4,#00e5ff)', 'linear-gradient(135deg,#ffcb55,#ff7ad9)']
  const arr = [...map.values()]
  arr.forEach((u, i) => { u.avatarBg = COLORS[i % COLORS.length] })
  arr.sort((a, b) => b.orders - a.orders)
  return arr.slice(0, 8)
})
const maxOrders = computed(() => Math.max(1, ...anchorRank.value.map(a => a.orders)))

// —— 筛选 ——
const activeStatus = ref('all')
const keyword = ref('')
const statusFilters = computed(() => [
  { key: 'all', label: '全部', count: allActivities.value.length },
  { key: 'pending', label: '待开始', count: allActivities.value.filter(a => a.status === 0).length },
  { key: 'running', label: '进行中', count: allActivities.value.filter(a => a.status === 1).length },
  { key: 'ended', label: '已结束', count: allActivities.value.filter(a => a.status === 2).length },
  { key: 'cancelled', label: '已取消', count: allActivities.value.filter(a => a.status === 3).length },
])
const filtered = computed(() => {
  let res = [...allActivities.value]
  if (activeStatus.value !== 'all') {
    const map = { pending: 0, running: 1, ended: 2, cancelled: 3 }
    res = res.filter(a => a.status === map[activeStatus.value])
  }
  if (keyword.value) {
    const k = keyword.value.toLowerCase()
    res = res.filter(a => a.name.toLowerCase().includes(k) || a.anchor.toLowerCase().includes(k))
  }
  return res
})

// —— 异常 ——
const alerts = ref([
  { level: 'danger', icon: '🚨', title: '活动 A005 库存异常', desc: '5 分钟内售出 68 件，疑似刷单', at: Date.now() - 2 * 60000 },
  { level: 'warning', icon: '⚠️', title: '主播 GlowQueen 直播中断', desc: '推流已断开 3 分钟，活动 A006 仍在进行中', at: Date.now() - 5 * 60000 },
  { level: 'warning', icon: '📈', title: '订单峰值告警', desc: '当前 QPS 1240，超过阈值 1000', at: Date.now() - 8 * 60000 },
  { level: 'info', icon: '✨', title: '新主播上线', desc: 'SnackKing 完成首播认证', at: Date.now() - 30 * 60000 },
])

// —— 工具 ——
function statusLabel(s) { return { 0: '待开始', 1: '进行中', 2: '已结束', 3: '已取消' }[s] || '未知' }
function statusKey(s) { return { 0: 'pending', 1: 'running', 2: 'ended', 3: 'cancelled' }[s] || 'pending' }
function sellPct(a) { return a.stockTotal ? (a.sold / a.stockTotal) * 100 : 0 }
function formatTime(ts) {
  if (!ts) return '-'
  const d = new Date(ts)
  const pad = n => String(n).padStart(2, '0')
  return `${d.getMonth() + 1}/${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}
function onView(a) { showToast(`查看活动 ${a.id}（待实现）`, 'info') }
function onForceStop(a) {
  if (!confirm(`强制下架「${a.name}」？将取消所有未支付订单。`)) return
  a.status = 3
  showToast(`已强制下架 ${a.id}`, 'warning')
}
</script>

<style scoped>
.act-overview { display: flex; flex-direction: column; gap: 16px; }

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
  display: grid;
  grid-template-columns: 40px 1fr auto;
  gap: 12px;
  align-items: center;
  padding: 16px 18px;
  border-radius: var(--radius);
  border: 1px solid var(--border-faint);
  background: var(--bg-card);
}
.kpi--running { border-color: rgba(255,77,79,0.3); box-shadow: 0 0 12px rgba(255,77,79,0.1); }
.kpi--revenue { border-color: rgba(255,203,85,0.3); }
.kpi__icon { font-size: 22px; text-align: center; }
.kpi__main { display: flex; flex-direction: column; }
.kpi__value { font-family: var(--font-num); font-size: 22px; font-weight: 700; color: var(--text-strong); }
.kpi__label { font-size: 11px; color: var(--text-muted); }
.kpi__delta { font-size: 11px; }
.kpi__delta.up { color: var(--success); }
.kpi__delta.down { color: var(--danger); }

/* —— 双栏 —— */
.dual { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
@media (max-width: 1100px) { .dual { grid-template-columns: 1fr; } }

.panel {
  border-radius: var(--radius);
  padding: 18px 20px;
  border: 1px solid var(--border-faint);
  background: var(--bg-card);
}
.panel__head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 14px; flex-wrap: wrap; gap: 10px; }
.panel__head h3 { margin: 0; font-family: var(--font-display); font-size: 15px; color: var(--text-strong); letter-spacing: 0.06em; }
.panel__hint { font-size: 11px; color: var(--text-dim); }

/* —— donut —— */
.donut-wrap { display: grid; grid-template-columns: 200px 1fr; gap: 20px; align-items: center; }
@media (max-width: 600px) { .donut-wrap { grid-template-columns: 1fr; } }
.donut { width: 200px; height: 200px; }
.donut__num { font-family: var(--font-num); font-size: 32px; font-weight: 700; fill: var(--text-strong); }
.donut__label { font-size: 11px; fill: var(--text-muted); }
.donut-legend { list-style: none; padding: 0; margin: 0; display: flex; flex-direction: column; gap: 8px; }
.donut-legend li { display: grid; grid-template-columns: 12px 1fr 40px 50px; gap: 8px; align-items: center; font-size: 12px; }
.donut-legend__dot { width: 10px; height: 10px; border-radius: 50%; }
.donut-legend__label { color: var(--text); }
.donut-legend__count { font-family: var(--font-num); color: var(--text-muted); text-align: right; }
.donut-legend__pct { font-family: var(--font-num); color: var(--neon-cyan); text-align: right; }

/* —— 主播排行 —— */
.anchor-rank { list-style: none; padding: 0; margin: 0; display: flex; flex-direction: column; gap: 10px; }
.anchor-rank__item { display: grid; grid-template-columns: 28px 32px 1fr 1fr 50px; gap: 10px; align-items: center; }
.anchor-rank__no {
  width: 24px; height: 24px;
  display: grid; place-items: center;
  border-radius: 50%;
  font-family: var(--font-num);
  font-size: 12px;
  font-weight: 700;
  background: rgba(86,88,122,0.2);
  color: var(--text-muted);
}
.no--1 { background: linear-gradient(135deg,#ffd666,#ff8a00); color: #07081a; box-shadow: 0 0 8px rgba(255,214,102,0.5); }
.no--2 { background: linear-gradient(135deg,#c9cdd4,#8a8fb5); color: #07081a; }
.no--3 { background: linear-gradient(135deg,#ffa572,#ff7ad9); color: #07081a; }
.anchor-rank__avatar {
  width: 32px; height: 32px;
  border-radius: 8px;
  display: grid; place-items: center;
  font-family: var(--font-display);
  font-weight: 700;
  color: #07081a;
  font-size: 14px;
}
.anchor-rank__info { display: flex; flex-direction: column; min-width: 0; }
.anchor-rank__name { font-size: 13px; color: var(--text-strong); font-weight: 600; }
.anchor-rank__meta { font-size: 11px; color: var(--text-muted); font-family: var(--font-num); }
.anchor-rank__bar { height: 6px; border-radius: 3px; background: rgba(7,8,26,0.6); overflow: hidden; }
.anchor-rank__bar-fill { height: 100%; background: linear-gradient(90deg, var(--neon-purple), var(--neon-cyan)); border-radius: 3px; transition: width 0.4s; }
.anchor-rank__orders { font-family: var(--font-num); font-weight: 700; color: var(--text-strong); text-align: right; }

/* —— 筛选 —— */
.filters { display: flex; gap: 4px; align-items: center; flex-wrap: wrap; }
.filter {
  padding: 5px 10px;
  border-radius: 999px;
  border: 1px solid transparent;
  background: transparent;
  color: var(--text-muted);
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s;
  display: inline-flex; align-items: center; gap: 5px;
}
.filter:hover { color: var(--text); background: rgba(138,99,255,0.06); }
.filter.is-on { color: var(--text-strong); background: rgba(138,99,255,0.16); border-color: var(--border-soft); }
.filter__count { font-family: var(--font-num); font-size: 10px; padding: 0 5px; border-radius: 999px; background: rgba(7,8,26,0.6); }
.filters input {
  width: 180px; height: 28px; padding: 0 10px;
  background: rgba(7,8,26,0.6); border: 1px solid var(--border-faint);
  border-radius: var(--radius); color: var(--text); font-size: 12px;
}
.filters input:focus { outline: none; border-color: var(--neon-purple); }

/* —— 表格 —— */
.table-wrap { overflow-x: auto; }
table { width: 100%; border-collapse: collapse; min-width: 880px; }
th, td { padding: 10px 12px; text-align: left; font-size: 13px; border-bottom: 1px solid var(--border-faint); }
th { color: var(--text-muted); font-weight: 500; font-size: 11px; letter-spacing: 0.06em; background: rgba(7,8,26,0.4); text-transform: uppercase; }
tbody tr:last-child td { border-bottom: none; }
tbody tr:hover { background: rgba(138,99,255,0.04); }
.num { font-family: var(--font-num); color: var(--text); }
.sell-rate { display: flex; align-items: center; gap: 6px; }
.sell-rate__bar { width: 80px; height: 6px; border-radius: 3px; background: rgba(7,8,26,0.6); overflow: hidden; }
.sell-rate__fill { height: 100%; background: linear-gradient(90deg, var(--success), var(--neon-cyan)); border-radius: 3px; transition: width 0.4s; }
.sell-rate__num { font-family: var(--font-num); font-size: 11px; color: var(--text-muted); min-width: 30px; }
.status-tag {
  padding: 2px 8px; border-radius: 999px; font-size: 11px; font-weight: 600;
}
.status-tag--pending { background: rgba(255,203,85,0.14); color: var(--warning); }
.status-tag--running { background: rgba(255,84,112,0.14); color: var(--danger); }
.status-tag--ended { background: rgba(138,99,255,0.14); color: var(--neon-purple); }
.status-tag--cancelled { background: rgba(86,88,122,0.2); color: var(--text-dim); }
.op {
  padding: 4px 9px; border-radius: 5px;
  border: 1px solid var(--border-faint);
  background: transparent; color: var(--text-muted);
  font-size: 11px; cursor: pointer; transition: all 0.2s;
  margin-right: 4px;
}
.op:hover { color: var(--text-strong); border-color: var(--border-strong); }
.op--view { color: var(--neon-cyan); border-color: rgba(0,229,255,0.3); }
.op--view:hover { background: rgba(0,229,255,0.1); }
.op--stop { color: var(--danger); border-color: rgba(255,84,112,0.3); }
.op--stop:hover { background: rgba(255,84,112,0.1); }

/* —— 异常 —— */
.alert-panel { border-color: rgba(255,84,112,0.25); }
.alert-list { list-style: none; padding: 0; margin: 0; display: flex; flex-direction: column; gap: 8px; }
.alert {
  display: grid;
  grid-template-columns: 32px 1fr auto;
  gap: 12px;
  align-items: center;
  padding: 10px 12px;
  border-radius: var(--radius);
  border-left: 3px solid;
}
.alert--danger { background: rgba(255,84,112,0.08); border-left-color: var(--danger); }
.alert--warning { background: rgba(255,203,85,0.06); border-left-color: var(--warning); }
.alert--info { background: rgba(0,229,255,0.06); border-left-color: var(--neon-cyan); }
.alert__icon { font-size: 18px; text-align: center; }
.alert__body { display: flex; flex-direction: column; gap: 2px; min-width: 0; }
.alert__title { font-size: 13px; color: var(--text-strong); font-weight: 600; }
.alert__desc { font-size: 11px; color: var(--text-muted); }
.alert__time { font-size: 11px; color: var(--text-dim); }
</style>
