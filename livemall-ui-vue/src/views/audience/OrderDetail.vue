<template>
  <div class="od container">
    <button class="back" @click="$router.push('/orders')">← 返回订单列表</button>

    <div v-if="order" class="od__grid">
      <!-- 左：订单卡 + 时间线 -->
      <div class="od__main">
        <!-- 状态横幅 -->
        <div class="status-banner glass" :class="`banner--${order.status}`">
          <div class="status-banner__icon">{{ statusIcon(order.status) }}</div>
          <div class="status-banner__text">
            <h2>{{ statusLabel(order.status) }}</h2>
            <p v-if="order.status === ORDER_STATUS.PENDING">
              请在 <span class="num countdown" :class="{ 'is-urgent': remainMs(order) < 60000 }">{{ countdownText }}</span> 内完成支付，超时自动取消
            </p>
            <p v-else-if="order.status === ORDER_STATUS.PAID">订单已支付，感谢购买</p>
            <p v-else-if="order.status === ORDER_STATUS.CANCELLED">{{ order.cancelReason || '订单已取消' }}</p>
            <p v-else-if="order.status === ORDER_STATUS.REFUNDED">退款已到账</p>
          </div>
        </div>

        <!-- 商品 -->
        <div class="product glass">
          <div class="product__thumb" />
          <div class="product__info">
            <h3>{{ order.activityName }}</h3>
            <p>秒杀活动 · 限量商品</p>
          </div>
          <div class="product__price">
            <span class="num">¥{{ order.price }}</span>
            <span class="product__orig">¥{{ order.origPrice }}</span>
            <span class="product__off">{{ discount }}折</span>
          </div>
        </div>

        <!-- 状态时间线 -->
        <div class="timeline glass">
          <h3 class="timeline__title">订单进度</h3>
          <ol class="timeline__list">
            <li v-for="step in timeline" :key="step.key" class="timeline__item"
              :class="{ 'is-done': step.done, 'is-current': step.current }">
              <span class="timeline__dot" />
              <div class="timeline__content">
                <span class="timeline__label">{{ step.label }}</span>
                <span v-if="step.time" class="timeline__time num">{{ formatTime(step.time) }}</span>
                <span v-else-if="step.current && step.key === 'pending'" class="timeline__time">剩余 {{ countdownText }}</span>
              </div>
            </li>
          </ol>
        </div>
      </div>

      <!-- 右：信息 + 操作 -->
      <aside class="od__side">
        <div class="info glass">
          <h3 class="info__title">订单信息</h3>
          <dl>
            <dt>订单号</dt><dd class="num">{{ order.orderNo }}</dd>
            <dt>下单时间</dt><dd class="num">{{ formatTime(order.createdAt) }}</dd>
            <dt v-if="order.paidAt">支付时间</dt><dd v-if="order.paidAt" class="num">{{ formatTime(order.paidAt) }}</dd>
            <dt v-if="order.cancelledAt">取消时间</dt><dd v-if="order.cancelledAt" class="num">{{ formatTime(order.cancelledAt) }}</dd>
            <dt v-if="order.refundedAt">退款时间</dt><dd v-if="order.refundedAt" class="num">{{ formatTime(order.refundedAt) }}</dd>
            <dt>数量</dt><dd>{{ order.quantity }}</dd>
            <dt>实付</dt><dd class="num info__price">¥{{ order.price }}</dd>
          </dl>
        </div>

        <div class="actions">
          <template v-if="order.status === ORDER_STATUS.PENDING">
            <NeonButton variant="seckill" block @click="onPay">立即支付 ¥{{ order.price }}</NeonButton>
            <button class="act-ghost" @click="onCancel">取消订单</button>
          </template>
          <template v-else-if="order.status === ORDER_STATUS.PAID">
            <button class="act-ghost" @click="onRefund">申请退款</button>
          </template>
          <button v-else class="act-ghost" @click="$router.push('/')">再去逛逛</button>
        </div>
      </aside>
    </div>

    <!-- 订单不存在 -->
    <div v-else class="notfound">
      <div class="notfound__code num">404</div>
      <h2>订单不存在</h2>
      <p>订单号 {{ $route.params.orderNo }} 找不到，可能已被清理</p>
      <NeonButton variant="ghost" @click="$router.push('/orders')">回订单列表</NeonButton>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, onBeforeUnmount, ref } from 'vue'
import { useRoute } from 'vue-router'
import { orderStore, remainMs } from '@/stores/order'
import { ORDER_STATUS, ORDER_STATUS_LABEL } from '@/constants'
import { showToast } from '@/utils/toast'
import NeonButton from '@/components/base/NeonButton.vue'

const route = useRoute()
const order = ref(null)
const tick = ref(0)
let timer = null

function refresh() {
  order.value = orderStore.detail(route.params.orderNo)
}
onMounted(() => {
  refresh()
  timer = setInterval(() => { tick.value++; refresh() }, 1000)
})
onBeforeUnmount(() => clearInterval(timer))

const discount = computed(() => order.value ? Math.round((order.value.price / order.value.origPrice) * 10) : 0)

const countdownText = computed(() => {
  void tick.value
  if (!order.value) return '--:--'
  const ms = remainMs(order.value)
  if (ms <= 0) return '已超时'
  const s = Math.floor(ms / 1000)
  const m = Math.floor(s / 60)
  return `${String(m).padStart(2, '0')}:${String(s % 60).padStart(2, '0')}`
})

const timeline = computed(() => {
  if (!order.value) return []
  const o = order.value
  const steps = [
    { key: 'created', label: '订单创建', time: o.createdAt, done: true, current: false },
    { key: 'pending', label: '等待支付', time: null, done: o.status !== ORDER_STATUS.PENDING, current: o.status === ORDER_STATUS.PENDING },
  ]
  if (o.paidAt || o.status === ORDER_STATUS.PAID) {
    steps.push({ key: 'paid', label: '支付完成', time: o.paidAt, done: o.status !== ORDER_STATUS.PENDING, current: o.status === ORDER_STATUS.PAID })
  }
  if (o.cancelledAt || o.status === ORDER_STATUS.CANCELLED) {
    steps.push({ key: 'cancelled', label: '订单取消', time: o.cancelledAt, done: true, current: o.status === ORDER_STATUS.CANCELLED })
  }
  if (o.refundedAt || o.status === ORDER_STATUS.REFUNDED) {
    steps.push({ key: 'refunded', label: '退款完成', time: o.refundedAt, done: true, current: o.status === ORDER_STATUS.REFUNDED })
  }
  return steps
})

function statusLabel(s) { return ORDER_STATUS_LABEL[s] || '未知' }
function statusIcon(s) {
  return { 0: '⏳', 1: '✓', 2: '×', 3: '↩' }[s] || '·'
}
function formatTime(t) {
  if (!t) return ''
  const d = new Date(t)
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}

function onPay() {
  if (orderStore.pay(route.params.orderNo)) {
    showToast('支付成功', 'success')
    refresh()
  }
}
function onCancel() {
  if (orderStore.cancel(route.params.orderNo, '用户主动取消')) {
    showToast('订单已取消', 'info')
    refresh()
  }
}
function onRefund() {
  if (orderStore.refund(route.params.orderNo)) {
    showToast('退款已发起', 'info')
    refresh()
  }
}
</script>

<style scoped>
.od { padding-top: 16px; }
.back { color: var(--text-muted); font-size: 13px; padding: 4px 8px; border-radius: 6px; margin-bottom: 16px; transition: color 0.2s; }
.back:hover { color: var(--neon-cyan); }

.od__grid { display: grid; grid-template-columns: 1fr 320px; gap: 18px; align-items: start; }
.od__main { display: flex; flex-direction: column; gap: 14px; }
.od__side { display: flex; flex-direction: column; gap: 14px; position: sticky; top: 84px; }

/* 状态横幅 */
.status-banner { display: flex; align-items: center; gap: 18px; padding: 24px 26px; animation: float-up 0.4s var(--ease-out-expo) both; }
.status-banner__icon { width: 52px; height: 52px; border-radius: 50%; display: grid; place-items: center; font-size: 24px; flex-shrink: 0; }
.status-banner__text h2 { font-size: 22px; margin-bottom: 4px; }
.status-banner__text p { color: var(--text-muted); font-size: 13px; }
.banner--0 .status-banner__icon { background: rgba(255, 203, 85, 0.15); border: 1px solid var(--warning); color: var(--warning); box-shadow: 0 0 16px rgba(255, 203, 85, 0.3); }
.banner--1 .status-banner__icon { background: rgba(82, 229, 164, 0.15); border: 1px solid var(--success); color: var(--success); box-shadow: 0 0 16px rgba(82, 229, 164, 0.3); }
.banner--2 .status-banner__icon { background: var(--bg-card); border: 1px solid var(--text-dim); color: var(--text-dim); }
.banner--3 .status-banner__icon { background: rgba(76, 201, 240, 0.15); border: 1px solid var(--info); color: var(--info); }
.countdown { color: var(--warning); font-weight: 700; font-size: 16px; }
.countdown.is-urgent { color: var(--danger); animation: countdown-urgent 1s ease-in-out infinite; }

/* 商品 */
.product { display: flex; align-items: center; gap: 16px; padding: 18px 20px; }
.product__thumb { width: 64px; height: 64px; border-radius: 10px; background: linear-gradient(135deg, var(--neon-purple), var(--neon-cyan)); flex-shrink: 0; }
.product__info { flex: 1; min-width: 0; }
.product__info h3 { font-size: 16px; margin-bottom: 4px; }
.product__info p { font-size: 12px; color: var(--text-dim); }
.product__price { text-align: right; display: flex; flex-direction: column; align-items: flex-end; gap: 2px; }
.product__price .num { font-family: var(--font-num); font-size: 22px; font-weight: 800; color: var(--seckill-to); }
.product__orig { font-size: 12px; color: var(--text-dim); text-decoration: line-through; }
.product__off { font-size: 10px; padding: 2px 6px; border-radius: 4px; background: rgba(255, 77, 79, 0.2); color: var(--seckill-from); }

/* 时间线 */
.timeline { padding: 20px 22px; }
.timeline__title { font-size: 15px; margin-bottom: 18px; }
.timeline__list { position: relative; padding-left: 6px; }
.timeline__list::before { content: ''; position: absolute; left: 11px; top: 8px; bottom: 8px; width: 1px; background: var(--border-soft); }
.timeline__item { position: relative; display: flex; gap: 16px; padding: 8px 0; }
.timeline__dot { width: 11px; height: 11px; border-radius: 50%; background: var(--bg-700); border: 2px solid var(--border-strong); flex-shrink: 0; margin-top: 4px; z-index: 1; transition: all 0.3s; }
.timeline__item.is-done .timeline__dot { background: var(--neon-cyan); border-color: var(--neon-cyan); box-shadow: 0 0 10px var(--neon-cyan-soft); }
.timeline__item.is-current .timeline__dot { background: var(--warning); border-color: var(--warning); box-shadow: 0 0 12px rgba(255, 203, 85, 0.6); animation: breathe 1.4s ease-in-out infinite; }
.timeline__content { display: flex; flex-direction: column; gap: 2px; }
.timeline__label { font-size: 14px; color: var(--text); }
.timeline__item.is-done .timeline__label { color: var(--text-strong); }
.timeline__item:not(.is-done):not(.is-current) .timeline__label { color: var(--text-dim); }
.timeline__time { font-size: 12px; color: var(--text-dim); }

/* 信息卡 */
.info { padding: 20px 22px; }
.info__title { font-size: 15px; margin-bottom: 16px; }
.info dl { display: grid; grid-template-columns: auto 1fr; gap: 10px 14px; margin: 0; }
.info dt { font-size: 12px; color: var(--text-dim); letter-spacing: 0.08em; }
.info dd { margin: 0; font-size: 13px; color: var(--text); text-align: right; word-break: break-all; }
.info__price { color: var(--seckill-to); font-weight: 700; font-size: 18px; }

/* 操作 */
.actions { display: flex; flex-direction: column; gap: 10px; }
.act-ghost {
  padding: 11px 16px; border-radius: var(--radius);
  background: var(--bg-card); border: 1px solid var(--border-soft);
  color: var(--text-muted); font-family: var(--font-display); font-size: 13px;
  transition: all 0.2s;
}
.act-ghost:hover { border-color: var(--neon-purple); color: var(--text-strong); }

/* 不存在 */
.notfound { text-align: center; padding: 80px 24px; display: flex; flex-direction: column; align-items: center; gap: 12px; }
.notfound__code { font-size: 90px; font-weight: 900; color: transparent; -webkit-text-stroke: 2px var(--neon-cyan); text-shadow: 0 0 30px var(--neon-cyan-soft); line-height: 1; }
.notfound h2 { font-size: 24px; }
.notfound p { color: var(--text-muted); font-size: 14px; }

@media (max-width: 880px) {
  .od__grid { grid-template-columns: 1fr; }
  .od__side { position: static; }
}
</style>
