<template>
  <Teleport to="body">
    <div v-if="visible" class="drawer-mask" @click.self="emit('close')">
      <div class="drawer card">
        <template v-if="activity">
          <button class="close-btn" @click="emit('close')">✕</button>

          <!-- 商品区 -->
          <div class="product">
            <div class="product-img" :style="{ background: '#2a2a38' }">🛍️</div>
            <div class="product-info">
              <div class="product-name">{{ activity.title }}</div>
              <div class="price-row">
                <span class="price">¥{{ activity.seckillPrice }}</span>
                <span class="orig-price">¥{{ activity.originalPrice }}</span>
              </div>
            </div>
          </div>

          <!-- 倒计时 + 库存 -->
          <div class="meta-row">
            <div class="countdown">
              <span class="cd-label">{{ status === 'PROCESSING' ? '距结束' : '距开始' }}</span>
              <span class="cd-value">{{ cdText }}</span>
            </div>
            <div class="stock">限量 {{ activity.totalStock }} 件</div>
          </div>

          <!-- CTA 按钮（六态） -->
          <button
            class="cta"
            :class="btnClass"
            :disabled="btnDisabled"
            @click="onPurchase"
          >
            {{ btnText }}
          </button>
        </template>
      </div>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { ref, computed, watch, onUnmounted } from 'vue'
import { useSeckill } from '@/composables/useSeckill'
import { serverNow } from '@/utils/time'
import type { SeckillActivity, SeckillStatus } from '@/types/seckill'

const props = defineProps<{
  visible: boolean
  activity: SeckillActivity | null
}>()
const emit = defineEmits<{ close: [] }>()

const { seckill, purchase } = useSeckill()

// ===== 倒计时状态（必须先于 watch 声明，immediate 会同步执行回调） =====
const now = ref(serverNow())
let ticker: ReturnType<typeof setInterval> | undefined

function startTicker() {
  stopTicker()
  now.value = serverNow()
  ticker = setInterval(() => { now.value = serverNow() }, 100)
}
function stopTicker() {
  if (ticker) { clearInterval(ticker); ticker = undefined }
}
onUnmounted(stopTicker)

// 打开抽屉时绑定当前活动并重置下单阶段
watch(() => [props.visible, props.activity], () => {
  if (props.visible && props.activity) {
    seckill.currentActivity = props.activity
    seckill.resetOrderPhase()
    startTicker()
  } else {
    stopTicker()
  }
}, { immediate: true })

const status = computed<SeckillStatus>(() => seckill.seckillStatus)

const cdText = computed(() => {
  const a = props.activity
  if (!a) return '--:--:--'
  const target = status.value === 'PROCESSING'
    ? new Date(a.endTime).getTime()
    : new Date(a.startTime).getTime()
  const remain = Math.max(0, target - now.value)
  const h = Math.floor(remain / 3600_000)
  const m = Math.floor((remain % 3600_000) / 60_000)
  const s = Math.floor((remain % 60_000) / 1000)
  const ms = Math.floor((remain % 1000) / 100)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${pad(h)}:${pad(m)}:${pad(s)}.${ms}`
})

// ===== CTA 按钮六态 =====
const btnText = computed(() => {
  switch (status.value) {
    case 'PENDING': return cdText.value
    case 'READY': return '即将开始'
    case 'PROCESSING': return '立即抢购'
    case 'QUEUING': return '排队中...'
    case 'SUCCESS': return '抢购成功！'
    case 'FAILED': return '已抢光'
  }
})
const btnDisabled = computed(() => status.value !== 'PROCESSING')
const btnClass = computed(() => ({
  'cta-red': status.value === 'PROCESSING',
  'cta-orange': status.value === 'QUEUING',
  'cta-green': status.value === 'SUCCESS',
  'cta-gray': status.value === 'PENDING' || status.value === 'READY' || status.value === 'FAILED',
}))

async function onPurchase() {
  if (!props.activity) return
  const okRes = await purchase(props.activity.id)
  if (okRes) {
    // 抢购成功：3s 后自动关闭
    setTimeout(() => emit('close'), 3000)
  } else {
    // 失败（超时/售罄）：恢复状态机，允许重试
    seckill.resetOrderPhase()
  }
}
</script>

<style scoped>
.drawer-mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.6);
  z-index: 1000;
  display: flex;
  align-items: flex-end;
  justify-content: center;
}
.drawer {
  width: 100%;
  max-width: 480px;
  border-radius: 16px 16px 0 0;
  padding: 24px;
  position: relative;
  animation: slide-up 0.25s ease;
}
@keyframes slide-up {
  from { transform: translateY(40px); opacity: 0; }
  to { transform: translateY(0); opacity: 1; }
}
.close-btn {
  position: absolute;
  top: 16px;
  right: 16px;
  color: var(--text-secondary);
  font-size: 16px;
}

.product { display: flex; gap: 14px; }
.product-img {
  width: 88px;
  height: 88px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 40px;
  flex-shrink: 0;
}
.product-name { font-size: 15px; font-weight: 600; line-height: 1.4; }
.price-row { margin-top: 8px; display: flex; align-items: baseline; gap: 8px; }
.price {
  font-family: var(--font-mono);
  font-size: 24px;
  font-weight: 700;
  color: var(--accent-red);
}
.orig-price {
  font-size: 13px;
  color: var(--text-secondary);
  text-decoration: line-through;
}

.meta-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin: 18px 0;
}
.cd-label { font-size: 12px; color: var(--text-secondary); margin-right: 8px; }
.cd-value { font-family: var(--font-mono); font-size: 18px; color: var(--accent-gold); }
.stock { font-size: 12px; color: var(--text-secondary); }

.cta {
  width: 100%;
  padding: 14px;
  border-radius: 10px;
  font-size: 16px;
  font-weight: 700;
  font-family: var(--font-mono);
  transition: transform 0.1s;
}
.cta-red {
  background: var(--accent-red);
  color: #fff;
  animation: breathe 1.6s infinite;
}
.cta-red:active { transform: scale(0.97); }
.cta-orange { background: #ff9f43; color: #fff; }
.cta-green { background: var(--status-green); color: #fff; }
.cta-gray { background: var(--bg-primary); color: var(--text-secondary); border: 1px solid var(--border); }
@keyframes breathe {
  0%, 100% { box-shadow: 0 0 0 0 rgba(255, 44, 85, 0.5); }
  50% { box-shadow: 0 0 16px 2px rgba(255, 44, 85, 0.5); }
}
</style>
