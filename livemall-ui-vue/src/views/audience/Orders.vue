<template>
  <div class="orders container">
    <header class="orders__head">
      <div>
        <h1 class="orders__title">我的订单</h1>
        <p class="orders__subtitle">秒杀订单 · 待支付 15 分钟内自动取消</p>
      </div>
      <button v-if="orders.length" class="orders__clear" @click="onClear">清空演示数据</button>
    </header>

    <!-- 状态筛选 -->
    <div class="filters">
      <button v-for="f in filters" :key="f.value" class="filter"
        :class="{ 'is-active': activeFilter === f.value }"
        @click="activeFilter = f.value">
        {{ f.label }}
        <span class="filter__count">{{ countOf(f.value) }}</span>
      </button>
    </div>

    <!-- 列表 -->
    <div v-if="filtered.length" class="order-list">
      <article v-for="o in filtered" :key="o.orderNo" class="order-card glass" @click="goDetail(o.orderNo)">
        <div class="order-card__status" :class="`status--${o.status}`">
          <span class="order-card__status-dot" />
          {{ statusLabel(o.status) }}
        </div>
        <div class="order-card__body">
          <div class="order-card__name ellipsis">{{ o.activityName }}</div>
          <div class="order-card__meta">
            <span class="order-card__no num">{{ o.orderNo }}</span>
            <span class="order-card__time">{{ formatTime(o.createdAt) }}</span>
          </div>
        </div>
        <div class="order-card__price">
          <span class="num">¥{{ o.price }}</span>
          <span class="order-card__orig">¥{{ o.origPrice }}</span>
        </div>
        <div class="order-card__actions">
          <template v-if="o.status === ORDER_STATUS.PENDING">
            <span class="countdown" :class="{ 'is-urgent': remainMs(o) < 60000 }">
              <span class="countdown__label">剩余</span>
              <span class="num">{{ countdownText(o) }}</span>
            </span>
            <button class="act act--primary" @click.stop="onPay(o)">支付</button>
            <button class="act" @click.stop="onCancel(o)">取消</button>
          </template>
          <template v-else-if="o.status === ORDER_STATUS.PAID">
            <button class="act" @click.stop="onRefund(o)">申请退款</button>
          </template>
          <template v-else>
            <span class="order-card__done">{{ statusLabel(o.status) }}</span>
          </template>
        </div>
      </article>
    </div>

    <!-- 空态 -->
    <div v-else class="empty">
      <div class="empty__icon" aria-hidden="true">
        <svg viewBox="0 0 64 64" width="64" height="64"><rect x="10" y="18" width="44" height="34" rx="4" fill="none" stroke="currentColor" stroke-width="2"/><path d="M10 28h44M20 18v-6h24v6" fill="none" stroke="currentColor" stroke-width="2"/></svg>
      </div>
      <h3>还没有订单</h3>
      <p>去直播间抢个秒杀，订单会出现在这里</p>
      <NeonButton variant="purple" @click="$router.push('/')">去逛逛</NeonButton>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, onBeforeUnmount, ref } from 'vue'
import { useRouter } from 'vue-router'
import { orderStore, remainMs } from '@/stores/order'
import { ORDER_STATUS, ORDER_STATUS_LABEL } from '@/constants'
import { showToast } from '@/utils/toast'
import NeonButton from '@/components/base/NeonButton.vue'

const router = useRouter()

const filters = [
  { label: '全部', value: 'all' },
  { label: '待支付', value: ORDER_STATUS.PENDING },
  { label: '已支付', value: ORDER_STATUS.PAID },
  { label: '已取消', value: ORDER_STATUS.CANCELLED },
  { label: '已退款', value: ORDER_STATUS.REFUNDED },
]
const activeFilter = ref('all')

const orders = ref([])
const tick = ref(0)
let timer = null

function refresh() {
  orders.value = orderStore.list()
}
onMounted(() => {
  refresh()
  // 每 1s 仅 tick 驱动倒计时重算（不重复读 store，store 数据由用户操作触发更新）
  timer = setInterval(() => { tick.value++ }, 1000)
})
onBeforeUnmount(() => clearInterval(timer))

const filtered = computed(() => {
  void tick.value
  if (activeFilter.value === 'all') return orders.value
  return orders.value.filter((o) => o.status === activeFilter.value)
})

function countOf(value) {
  if (value === 'all') return orders.value.length
  return orders.value.filter((o) => o.status === value).length
}

function statusLabel(s) { return ORDER_STATUS_LABEL[s] || '未知' }

function formatTime(t) {
  const d = new Date(t)
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getMonth() + 1}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
}

function countdownText(o) {
  void tick.value
  const ms = remainMs(o)
  if (ms <= 0) return '已超时'
  const s = Math.floor(ms / 1000)
  const m = Math.floor(s / 60)
  return `${String(m).padStart(2, '0')}:${String(s % 60).padStart(2, '0')}`
}

function goDetail(orderNo) { router.push(`/orders/${orderNo}`) }

function onPay(o) {
  if (orderStore.pay(o.orderNo)) {
    showToast('支付成功', 'success')
    refresh()
  }
}
function onCancel(o) {
  if (orderStore.cancel(o.orderNo)) {
    showToast('已取消', 'info')
    refresh()
  }
}
function onRefund(o) {
  if (orderStore.refund(o.orderNo)) {
    showToast('退款已发起', 'info')
    refresh()
  }
}
function onClear() {
  orderStore.clear()
  refresh()
  showToast('已清空演示数据', 'info')
}
</script>

<style scoped>
.orders { padding-top: 16px; }
.orders__head { display: flex; align-items: flex-end; justify-content: space-between; margin-bottom: 20px; }
.orders__title { font-size: 28px; letter-spacing: 0.04em; }
.orders__subtitle { color: var(--text-muted); font-size: 13px; margin-top: 4px; }
.orders__clear { color: var(--text-dim); font-size: 12px; padding: 6px 12px; border-radius: 6px; border: 1px solid var(--border-faint); transition: all 0.2s; }
.orders__clear:hover { color: var(--danger); border-color: var(--danger); }

/* 筛选 */
.filters { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 20px; }
.filter {
  padding: 7px 16px; border-radius: 999px;
  background: var(--bg-card); border: 1px solid var(--border-soft);
  color: var(--text-muted); font-size: 13px; font-family: var(--font-display);
  display: inline-flex; align-items: center; gap: 6px;
  transition: all 0.2s var(--ease-out-expo);
}
.filter:hover { color: var(--text); border-color: var(--border-strong); }
.filter.is-active {
  color: var(--text-strong);
  border-color: var(--neon-purple);
  background: rgba(138, 99, 255, 0.12);
  box-shadow: 0 0 12px var(--neon-purple-soft);
}
.filter__count { font-family: var(--font-num); font-size: 11px; color: var(--neon-cyan); }

/* 列表 */
.order-list { display: flex; flex-direction: column; gap: 12px; }
.order-card {
  display: grid;
  grid-template-columns: auto 1fr auto auto;
  align-items: center;
  gap: 18px;
  padding: 16px 20px;
  cursor: pointer;
  transition: transform 0.2s var(--ease-out-expo), border-color 0.2s, box-shadow 0.2s;
  animation: float-up 0.4s var(--ease-out-expo) both;
}
.order-card:hover { transform: translateY(-2px); border-color: var(--border-strong); box-shadow: 0 8px 24px rgba(0,0,0,0.3); }

.order-card__status {
  display: inline-flex; align-items: center; gap: 6px;
  padding: 4px 10px; border-radius: 6px;
  font-family: var(--font-display); font-size: 11px; letter-spacing: 0.1em;
}
.order-card__status-dot { width: 6px; height: 6px; border-radius: 50%; }
.status--0 { color: var(--warning); background: rgba(255, 203, 85, 0.1); border: 1px solid rgba(255, 203, 85, 0.3); }
.status--0 .order-card__status-dot { background: var(--warning); box-shadow: 0 0 6px var(--warning); animation: breathe 1.4s ease-in-out infinite; }
.status--1 { color: var(--success); background: rgba(82, 229, 164, 0.1); border: 1px solid rgba(82, 229, 164, 0.3); }
.status--1 .order-card__status-dot { background: var(--success); }
.status--2 { color: var(--text-dim); background: var(--bg-card); border: 1px solid var(--border-faint); }
.status--2 .order-card__status-dot { background: var(--text-dim); }
.status--3 { color: var(--info); background: rgba(76, 201, 240, 0.1); border: 1px solid rgba(76, 201, 240, 0.3); }
.status--3 .order-card__status-dot { background: var(--info); }

.order-card__body { min-width: 0; }
.order-card__name { font-size: 15px; font-weight: 600; color: var(--text-strong); margin-bottom: 4px; }
.order-card__meta { display: flex; gap: 12px; font-size: 12px; color: var(--text-dim); }

.order-card__price { text-align: right; }
.order-card__price .num { font-family: var(--font-num); font-size: 20px; font-weight: 800; color: var(--seckill-to); }
.order-card__orig { font-size: 12px; color: var(--text-dim); text-decoration: line-through; display: block; }

.order-card__actions { display: flex; align-items: center; gap: 10px; min-width: 180px; justify-content: flex-end; }
.countdown { display: flex; flex-direction: column; align-items: flex-end; font-size: 11px; color: var(--text-dim); }
.countdown .num { font-size: 15px; color: var(--warning); font-weight: 700; }
.countdown.is-urgent .num { color: var(--danger); animation: countdown-urgent 1s ease-in-out infinite; }
.act {
  padding: 6px 14px; border-radius: 6px;
  background: var(--bg-card); border: 1px solid var(--border-soft);
  color: var(--text); font-size: 12px; font-family: var(--font-display);
  transition: all 0.18s var(--ease-out-expo);
}
.act:hover { border-color: var(--neon-purple); color: var(--text-strong); }
.act--primary {
  background: linear-gradient(135deg, var(--seckill-from), var(--seckill-to));
  border: none; color: #fff; box-shadow: 0 0 12px var(--seckill-glow);
}
.act--primary:hover { filter: brightness(1.1); }
.order-card__done { font-size: 12px; color: var(--text-dim); }

/* 空态 */
.empty { text-align: center; padding: 72px 24px; display: flex; flex-direction: column; align-items: center; gap: 12px; color: var(--text-muted); }
.empty__icon { color: var(--border-strong); margin-bottom: 6px; }
.empty h3 { font-size: 20px; color: var(--text); }
.empty p { font-size: 14px; }

@media (max-width: 720px) {
  .order-card { grid-template-columns: 1fr; gap: 10px; }
  .order-card__actions { justify-content: flex-start; }
}
</style>
