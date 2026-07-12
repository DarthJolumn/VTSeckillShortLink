<template>
  <button
    :type="type"
    :disabled="disabled || loading"
    class="neon-btn"
    :class="[`neon-btn--${variant}`, { 'is-block': block, 'is-loading': loading }]"
    @click="$emit('click', $event)"
  >
    <span v-if="loading" class="neon-btn__spinner" aria-hidden="true" />
    <span class="neon-btn__inner"><slot /></span>
  </button>
</template>

<script setup>
defineProps({
  type: { type: String, default: 'button' },
  variant: { type: String, default: 'purple' }, // purple / cyan / seckill / ghost
  block: { type: Boolean, default: false },
  disabled: { type: Boolean, default: false },
  loading: { type: Boolean, default: false },
})
defineEmits(['click'])
</script>

<style scoped>
.neon-btn {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 11px 22px;
  border-radius: var(--radius);
  font-family: var(--font-display);
  font-weight: 600;
  font-size: 15px;
  letter-spacing: 0.06em;
  color: var(--text-strong);
  cursor: pointer;
  overflow: hidden;
  isolation: isolate;
  transition: transform 0.18s var(--ease-out-expo), box-shadow 0.25s var(--ease-out-expo),
    filter 0.25s var(--ease-out-expo);
}
.neon-btn.is-block { width: 100%; }
.neon-btn:disabled {
  cursor: not-allowed;
  filter: grayscale(0.4) brightness(0.7);
  opacity: 0.7;
}
.neon-btn:not(:disabled):active { transform: translateY(1px) scale(0.985); }

/* 流光斜扫 */
.neon-btn::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  width: 30%;
  height: 100%;
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.35), transparent);
  transform: translateX(-120%) skewX(-18deg);
  z-index: 1;
}
.neon-btn:not(:disabled):hover::before {
  animation: sweep 0.9s var(--ease-out-expo);
}

.neon-btn__inner { position: relative; z-index: 2; }
.neon-btn__spinner {
  width: 14px;
  height: 14px;
  border-radius: 50%;
  border: 2px solid rgba(255, 255, 255, 0.35);
  border-top-color: #fff;
  animation: radar-spin 0.7s linear infinite;
}

/* 变体 */
.neon-btn--purple {
  background: linear-gradient(135deg, #6b4dff, #8a63ff 55%, #b07cff);
  box-shadow: var(--glow-purple);
}
.neon-btn--purple:not(:disabled):hover { box-shadow: 0 0 30px rgba(138, 99, 255, 0.8), 0 0 64px rgba(138, 99, 255, 0.3); }

.neon-btn--cyan {
  background: linear-gradient(135deg, #00bcd4, #00e5ff 60%, #66f0ff);
  color: #03121a;
  box-shadow: var(--glow-cyan);
}
.neon-btn--cyan:not(:disabled):hover { box-shadow: 0 0 30px rgba(0, 229, 255, 0.8), 0 0 64px rgba(0, 229, 255, 0.3); }

.neon-btn--seckill {
  background: linear-gradient(135deg, var(--seckill-from), var(--seckill-to));
  color: #fff;
  animation: neon-pulse 1.8s ease-in-out infinite;
}
.neon-btn--seckill:not(:disabled):hover { filter: brightness(1.1); }

.neon-btn--ghost {
  background: var(--bg-card);
  border: 1px solid var(--border-strong);
  color: var(--text);
  backdrop-filter: blur(10px);
}
.neon-btn--ghost:not(:disabled):hover {
  border-color: var(--neon-purple);
  box-shadow: 0 0 18px var(--neon-purple-soft);
}
</style>
