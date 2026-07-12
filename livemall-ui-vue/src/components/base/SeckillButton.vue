<template>
  <button
    type="button"
    class="seckill-btn"
    :class="`seckill-btn--${phase}`"
    :disabled="disabled"
    @click="$emit('click', $event)"
  >
    <span class="seckill-btn__bg" aria-hidden="true" />
    <span class="seckill-btn__inner">
      <template v-if="phase === 'pending'">
        <span class="seckill-btn__label">即将开始</span>
        <span class="seckill-btn__countdown"><slot name="countdown" /></span>
      </template>
      <template v-else-if="phase === 'running'">
        <span class="seckill-btn__label">{{ loading ? '抢购中…' : '立即抢购' }}</span>
      </template>
      <template v-else-if="phase === 'soldout'">已售罄</template>
      <template v-else-if="phase === 'ended'">活动结束</template>
      <template v-else>{{ label }}</template>
    </span>
  </button>
</template>

<script setup>
defineProps({
  phase: { type: String, default: 'pending' }, // pending / running / soldout / ended / idle
  loading: { type: Boolean, default: false },
  disabled: { type: Boolean, default: false },
  label: { type: String, default: '' },
})
defineEmits(['click'])
</script>

<style scoped>
.seckill-btn {
  position: relative;
  width: 100%;
  height: 52px;
  border-radius: var(--radius);
  font-family: var(--font-display);
  font-weight: 700;
  font-size: 17px;
  letter-spacing: 0.1em;
  color: #fff;
  cursor: pointer;
  overflow: hidden;
  isolation: isolate;
  transition: transform 0.16s var(--ease-out-expo);
}
.seckill-btn:disabled { cursor: not-allowed; }
.seckill-btn:not(:disabled):active { transform: translateY(2px) scale(0.985); }

.seckill-btn__bg {
  position: absolute; inset: 0; z-index: 0;
  transition: opacity 0.3s;
}
.seckill-btn__inner {
  position: relative; z-index: 1;
  display: inline-flex; align-items: center; justify-content: center; gap: 12px;
}

/* pending · 蓄力灰紫 */
.seckill-btn--pending .seckill-btn__bg {
  background: linear-gradient(135deg, rgba(138, 99, 255, 0.45), rgba(30, 33, 66, 0.9));
  border: 1px solid var(--border-strong);
}
.seckill-btn--pending .seckill-btn__label { color: var(--text); }
.seckill-btn--pending .seckill-btn__countdown { font-family: var(--font-num); color: var(--neon-cyan); }

/* running · 烈焰橙红 + 脉冲 */
.seckill-btn--running .seckill-btn__bg {
  background: linear-gradient(135deg, var(--seckill-from), var(--seckill-to));
}
.seckill-btn--running:not(:disabled) {
  animation: neon-pulse 1.6s ease-in-out infinite;
}

/* soldout · 灰化 */
.seckill-btn--soldout .seckill-btn__bg {
  background: linear-gradient(135deg, #2a2c45, #1a1c30);
  border: 1px solid var(--border-faint);
}
.seckill-btn--soldout { color: var(--text-dim); }

/* ended */
.seckill-btn--ended .seckill-btn__bg {
  background: linear-gradient(135deg, #1a1c30, #14162f);
  border: 1px solid var(--border-faint);
}
.seckill-btn--ended { color: var(--text-dim); }

/* idle */
.seckill-btn--idle .seckill-btn__bg {
  background: var(--bg-card);
  border: 1px solid var(--border-soft);
}
.seckill-btn--idle { color: var(--text-muted); }

/* 流光斜扫（running 时） */
.seckill-btn--running:not(:disabled)::after {
  content: '';
  position: absolute;
  top: 0; left: 0;
  width: 30%; height: 100%;
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.45), transparent);
  transform: translateX(-120%) skewX(-18deg);
  animation: sweep 1.4s var(--ease-out-expo) infinite;
  z-index: 2;
}
</style>
