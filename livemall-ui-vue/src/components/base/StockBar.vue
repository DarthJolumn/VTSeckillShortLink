<template>
  <div class="stock-bar" :class="{ 'is-critical': isCritical }">
    <div class="stock-bar__head">
      <span class="stock-bar__label">{{ isCritical ? '⚠️ 仅剩' : '剩余库存' }}</span>
      <span class="stock-bar__num num">
        <NumberFlip :value="stock" :urgent="isCritical" /><span class="stock-bar__total">/{{ total }}</span>
      </span>
    </div>
    <div class="stock-bar__track">
      <div class="stock-bar__fill" :style="{ width: pct + '%' }" :class="{ 'is-low': pct <= 20 }" />
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import NumberFlip from './NumberFlip.vue'
const props = defineProps({
  stock: { type: Number, default: 0 },
  total: { type: Number, default: 0 },
})
const pct = computed(() => (props.total ? Math.min(100, Math.round((props.stock / props.total) * 100)) : 0))
// ≤10% 进入"紧急"状态：数字翻牌加速 + 整体红色呼吸
const isCritical = computed(() => props.total > 0 && props.stock > 0 && props.stock / props.total <= 0.1)
</script>

<style scoped>
.stock-bar__head { display: flex; justify-content: space-between; align-items: baseline; margin-bottom: 6px; }
.stock-bar__label { font-size: 12px; color: var(--text-muted); letter-spacing: 0.1em; transition: color 0.3s; }
.stock-bar__num { font-size: 18px; font-weight: 700; color: var(--text-strong); }
.stock-bar__total { font-size: 13px; color: var(--text-dim); margin-left: 2px; }
.stock-bar__track { height: 8px; border-radius: 999px; background: rgba(7,8,26,0.6); overflow: hidden; border: 1px solid var(--border-faint); }
.stock-bar__fill {
  height: 100%;
  border-radius: 999px;
  background: linear-gradient(90deg, var(--success), var(--neon-cyan));
  box-shadow: 0 0 10px var(--neon-cyan-soft);
  transition: width 0.5s var(--ease-out-expo), background 0.3s;
}
.stock-bar__fill.is-low {
  background: linear-gradient(90deg, var(--seckill-from), var(--seckill-to));
  box-shadow: 0 0 10px var(--seckill-glow);
}
/* 紧急状态：整体抖动 + 标签变红 */
.stock-bar.is-critical .stock-bar__label { color: var(--seckill-from); animation: stock-shake 0.5s ease-in-out infinite; }
.stock-bar.is-critical .stock-bar__num { color: var(--seckill-from); text-shadow: 0 0 8px var(--seckill-glow); }
@keyframes stock-shake {
  0%, 100% { transform: translateX(0); }
  25%      { transform: translateX(-1.5px); }
  75%      { transform: translateX(1.5px); }
}
</style>
