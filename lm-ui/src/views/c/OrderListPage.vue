<template>
  <div class="orders-page">
    <div class="card panel">
      <h3>我的订单</h3>

      <div class="filter-tabs">
        <button
          v-for="f in filters"
          :key="f.key"
          :class="{ active: filter === f.key }"
          @click="filter = f.key"
        >
          {{ f.label }}
        </button>
      </div>

      <div v-if="filteredOrders.length === 0" class="empty">暂无订单</div>

      <div
        v-for="o in filteredOrders"
        :key="o.orderNo"
        class="order-row"
        @click="openDetail(o.orderNo)"
      >
        <div class="order-main">
          <div class="order-no">{{ o.orderNo }}</div>
          <div class="order-meta">
            活动 #{{ o.activityId }} · ¥{{ o.seckillPrice }} · {{ formatTime(o.createdAt) }}
          </div>
        </div>
        <div class="order-side" @click.stop>
          <span class="status" :class="statusClass(o.status)">{{ statusText(o.status) }}</span>
          <span v-if="o.status === 0" class="pay-cd">{{ payCountdown(o) }}</span>
          <button v-if="o.status === 0" class="btn-ghost cancel-btn" @click="onCancel(o.orderNo)">取消</button>
          <button v-if="o.status === 1" class="btn-ghost refund-btn" @click="onRefund(o.orderNo)">退款</button>
        </div>
      </div>
    </div>

    <!-- 订单详情弹窗 -->
    <Teleport to="body">
      <div v-if="detail" class="detail-mask" @click.self="detail = null">
        <div class="detail-dialog card">
          <button class="close-btn" @click="detail = null">✕</button>
          <h3>订单详情</h3>
          <div class="detail-rows">
            <div class="row"><span>订单号</span><b class="mono">{{ detail.orderNo }}</b></div>
            <div class="row"><span>活动 ID</span><b>{{ detail.activityId }}</b></div>
            <div class="row"><span>商品 ID</span><b>{{ detail.productId }}</b></div>
            <div class="row"><span>秒杀价</span><b class="red">¥{{ detail.seckillPrice }}</b></div>
            <div class="row">
              <span>状态</span>
              <b class="status" :class="statusClass(detail.status)">{{ statusText(detail.status) }}</b>
            </div>
          </div>
          <div class="timeline">
            <div class="tl-item"><i />创建：{{ formatFull(detail.createdAt) }}</div>
            <div v-if="detail.paidAt" class="tl-item"><i class="green" />支付：{{ formatFull(detail.paidAt) }}</div>
            <div v-if="detail.cancelledAt" class="tl-item"><i class="gray" />取消：{{ formatFull(detail.cancelledAt) }}</div>
          </div>
          <div class="detail-actions">
            <button v-if="detail.status === 0" class="btn-ghost" @click="onCancel(detail.orderNo); detail = null">取消订单</button>
            <button v-if="detail.status === 1" class="btn-ghost refund" @click="onRefund(detail.orderNo); detail = null">申请退款</button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useSeckillStore } from '@/stores/seckill'
import { serverNow } from '@/utils/time'
import { showToast } from '@/utils/toast'
import { ApiError } from '@/utils/http'
import type { SeckillOrder } from '@/types/seckill'

const seckillStore = useSeckillStore()

const filters = [
  { key: 'all', label: '全部' },
  { key: '0', label: '待支付' },
  { key: '1', label: '已支付' },
  { key: '2', label: '已取消' },
  { key: '3', label: '已退款' },
]
const filter = ref('all')
const detail = ref<SeckillOrder | null>(null)

const filteredOrders = computed(() => {
  if (filter.value === 'all') return seckillStore.orders
  return seckillStore.orders.filter(o => o.status === Number(filter.value))
})

// 待支付倒计时：createdAt + 15min - serverNow（后端超时取消扫描）
const PAY_TIMEOUT_MS = 15 * 60_000
const now = ref(serverNow())
let ticker: ReturnType<typeof setInterval> | undefined

onMounted(async () => {
  ticker = setInterval(() => { now.value = serverNow() }, 1000)
  try {
    await seckillStore.fetchOrders()
  } catch { /* 401 由拦截器处理 */ }
})
onUnmounted(() => { if (ticker) clearInterval(ticker) })

function payCountdown(o: SeckillOrder): string {
  const remain = new Date(o.createdAt).getTime() + PAY_TIMEOUT_MS - now.value
  if (remain <= 0) return '已超时'
  const m = Math.floor(remain / 60_000)
  const s = Math.floor((remain % 60_000) / 1000)
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
}

function statusText(s: number) {
  return { 0: '待支付', 1: '已支付', 2: '已取消', 3: '已退款' }[s] || '未知'
}
function statusClass(s: number) {
  return { 0: 'st-pending', 1: 'st-paid', 2: 'st-cancel', 3: 'st-refund' }[s] || ''
}

function formatTime(iso: string) {
  const d = new Date(iso)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getMonth() + 1}/${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}
function formatFull(iso: string) {
  const d = new Date(iso)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

async function openDetail(orderNo: string) {
  try {
    detail.value = await seckillStore.fetchOrderDetail(orderNo)
  } catch (e) {
    showToast(e instanceof ApiError ? e.message : '加载详情失败', 'error')
  }
}

async function onCancel(orderNo: string) {
  try {
    await seckillStore.cancelOrder(orderNo)
    showToast('订单已取消', 'success')
  } catch (e) {
    showToast(e instanceof ApiError ? e.message : '取消失败', 'error')
  }
}

async function onRefund(orderNo: string) {
  try {
    await seckillStore.refundOrder(orderNo)
    showToast('退款成功', 'success')
  } catch (e) {
    showToast(e instanceof ApiError ? e.message : '退款失败', 'error')
  }
}
</script>

<style scoped>
.panel { padding: 20px 24px; }
h3 { font-size: 16px; margin-bottom: 16px; }
.filter-tabs { display: flex; gap: 8px; margin-bottom: 16px; }
.filter-tabs button {
  padding: 6px 14px;
  border-radius: 16px;
  font-size: 13px;
  color: var(--text-secondary);
  border: 1px solid var(--border);
  transition: all 0.15s;
}
.filter-tabs button.active { color: var(--accent-red); border-color: var(--accent-red); }
.empty { text-align: center; color: var(--text-secondary); padding: 40px 0; font-size: 13px; }

.order-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 8px;
  border-bottom: 1px solid var(--border);
  cursor: pointer;
  border-radius: 8px;
  transition: background 0.15s;
}
.order-row:hover { background: var(--bg-primary); }
.order-row:last-child { border-bottom: none; }
.order-no { font-family: var(--font-mono); font-size: 14px; }
.order-meta { font-size: 12px; color: var(--text-secondary); margin-top: 4px; }
.order-side { display: flex; align-items: center; gap: 10px; }
.status { font-size: 13px; }
.st-pending { color: var(--accent-gold); }
.st-paid { color: var(--status-green); }
.st-cancel, .st-refund { color: var(--text-secondary); }
.pay-cd { font-family: var(--font-mono); font-size: 12px; color: var(--accent-red); }
.cancel-btn { padding: 5px 12px; font-size: 12px; }
.refund-btn { padding: 5px 12px; font-size: 12px; color: var(--accent-gold); border-color: var(--accent-gold); }

/* 详情弹窗 */
.detail-mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.6);
  z-index: 1000;
  display: flex;
  align-items: center;
  justify-content: center;
}
.detail-dialog {
  width: 400px;
  max-width: 92vw;
  padding: 24px;
  position: relative;
}
.close-btn { position: absolute; top: 14px; right: 16px; color: var(--text-secondary); }
.detail-rows { margin-top: 8px; }
.row {
  display: flex;
  justify-content: space-between;
  padding: 9px 0;
  font-size: 13px;
  border-bottom: 1px dashed var(--border);
}
.row span { color: var(--text-secondary); }
.mono { font-family: var(--font-mono); }
.red { color: var(--accent-red); }
.timeline { margin-top: 14px; display: flex; flex-direction: column; gap: 8px; }
.tl-item { font-size: 12px; color: var(--text-secondary); display: flex; align-items: center; gap: 8px; }
.tl-item i {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--accent-gold);
}
.tl-item i.green { background: var(--status-green); }
.tl-item i.gray { background: var(--text-secondary); }
.detail-actions { margin-top: 18px; display: flex; justify-content: flex-end; gap: 10px; }
.detail-actions .refund { color: var(--accent-gold); border-color: var(--accent-gold); }
</style>
