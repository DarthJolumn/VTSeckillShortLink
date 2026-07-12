<template>
  <transition name="biggift">
    <div v-if="active" class="biggift" :class="`biggift--${tier}`" aria-hidden="true">
      <!-- 拖尾粒子 -->
      <div class="biggift__trail">
        <span v-for="i in 14" :key="i" class="biggift__spark" :style="sparkStyle(i)" />
      </div>
      <!-- 主体 -->
      <div class="biggift__body">
        <div class="biggift__halo" />
        <span class="biggift__icon">{{ gift.icon }}</span>
        <div class="biggift__text">
          <span class="biggift__from">{{ gift.from }}</span>
          <span class="biggift__name">送出 {{ gift.name }} ×{{ gift.quantity }}</span>
        </div>
      </div>
      <!-- 底部祝福条 -->
      <div class="biggift__bar">
        <span class="biggift__bar-text">{{ blessing }}</span>
      </div>
    </div>
  </transition>
</template>

<script setup>
import { computed, ref, watch } from 'vue'

const props = defineProps({
  trigger: { type: Number, default: 0 },
  // { icon, name, from, quantity, price }
  payload: { type: Object, default: () => ({}) },
})

const active = ref(false)
let hideTimer = null

const gift = computed(() => ({
  icon: '🎁', name: '礼物', from: '神秘观众', quantity: 1, price: 0,
  ...props.payload,
}))

const tier = computed(() => {
  const p = gift.value.price || 0
  if (p >= 500) return 'legend'
  if (p >= 100) return 'epic'
  return 'rare'
})

const blessing = computed(() => ({
  legend: '感谢大佬的连发火箭 · 气氛拉满',
  epic: '跑车疾驰 · 主播收礼收到手软',
  rare: '玫瑰花瓣飘满直播间',
}[tier.value] || ''))

function fire() {
  if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) return
  active.value = true
  clearTimeout(hideTimer)
  hideTimer = setTimeout(() => { active.value = false }, 3200)
}

watch(() => props.trigger, (v) => { if (v > 0) fire() })

function sparkStyle(i) {
  const delay = (i * 0.08).toFixed(2)
  const y = (Math.sin(i) * 30).toFixed(0)
  const scale = (0.6 + (i % 3) * 0.2).toFixed(2)
  return { animationDelay: `${delay}s`, '--sy': `${y}px`, '--sc': scale }
}
</script>

<style scoped>
.biggift {
  position: fixed;
  top: 30%;
  left: 0;
  width: 100%;
  z-index: var(--z-modal);
  pointer-events: none;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14px;
}

/* 主体：从右飞入，悬停，从左飞出 */
.biggift__body {
  position: relative;
  display: flex;
  align-items: center;
  gap: 18px;
  padding: 14px 28px 14px 22px;
  border-radius: 999px;
  background: linear-gradient(135deg, rgba(20,22,48,0.92), rgba(10,11,26,0.88));
  border: 1px solid var(--border-strong);
  backdrop-filter: blur(14px);
  animation: biggift-fly 3.2s var(--ease-out-expo) both;
}
.biggift__halo {
  position: absolute;
  inset: -20px;
  border-radius: 999px;
  background: radial-gradient(closest-side, var(--halo, rgba(255,138,0,0.5)), transparent 70%);
  filter: blur(14px);
  z-index: -1;
  animation: biggift-pulse 1.2s ease-in-out infinite;
}
.biggift__icon {
  font-size: 52px;
  line-height: 1;
  filter: drop-shadow(0 0 16px var(--halo, rgba(255,138,0,0.7)));
  animation: biggift-bob 1.4s ease-in-out infinite;
}
.biggift__text {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.biggift__from {
  font-family: var(--font-display);
  font-weight: 600;
  font-size: 15px;
  color: var(--text-strong);
}
.biggift__name {
  font-size: 13px;
  color: var(--text-muted);
  letter-spacing: 0.04em;
}

/* 拖尾粒子 */
.biggift__trail {
  position: absolute;
  top: 50%;
  left: -200px;
  display: flex;
  gap: 8px;
  transform: translateY(-50%);
}
.biggift__spark {
  width: 6px; height: 6px;
  border-radius: 50%;
  background: var(--halo, #ff8a00);
  box-shadow: 0 0 8px var(--halo, #ff8a00);
  opacity: 0;
  animation: biggift-spark 1.6s var(--ease-out-expo) infinite;
}

/* 底部祝福条 */
.biggift__bar {
  padding: 6px 18px;
  border-radius: 999px;
  background: var(--bg-elevated);
  border: 1px solid var(--border-soft);
  font-family: var(--font-display);
  font-size: 12px;
  letter-spacing: 0.16em;
  color: var(--text);
  animation: biggift-bar 3.2s var(--ease-out-expo) both;
}

/* 档位配色 */
.biggift--rare { --halo: rgba(0, 229, 255, 0.55); }
.biggift--epic { --halo: rgba(138, 99, 255, 0.6); }
.biggift--legend { --halo: rgba(255, 138, 0, 0.7); }
.biggift--legend .biggift__icon { font-size: 64px; }

@keyframes biggift-fly {
  0%   { transform: translateX(110vw) rotate(-8deg); opacity: 0; }
  18%  { transform: translateX(0) rotate(0); opacity: 1; }
  78%  { transform: translateX(0) rotate(0); opacity: 1; }
  100% { transform: translateX(-110vw) rotate(8deg); opacity: 0; }
}
@keyframes biggift-pulse {
  0%, 100% { opacity: 0.6; transform: scale(1); }
  50% { opacity: 1; transform: scale(1.08); }
}
@keyframes biggift-bob {
  0%, 100% { transform: translateY(0) rotate(-3deg); }
  50% { transform: translateY(-6px) rotate(3deg); }
}
@keyframes biggift-spark {
  0% { opacity: 0; transform: translate(0, 0) scale(0.4); }
  40% { opacity: 1; transform: translate(40px, var(--sy, 0)) scale(var(--sc, 1)); }
  100% { opacity: 0; transform: translate(120px, calc(var(--sy, 0) * -1)) scale(0.2); }
}
@keyframes biggift-bar {
  0% { opacity: 0; transform: translateY(8px); }
  25% { opacity: 1; transform: translateY(0); }
  75% { opacity: 1; }
  100% { opacity: 0; transform: translateY(-6px); }
}

@media (prefers-reduced-motion: reduce) {
  .biggift__body, .biggift__bar, .biggift__halo, .biggift__icon, .biggift__spark {
    animation: none !important;
  }
}
</style>
