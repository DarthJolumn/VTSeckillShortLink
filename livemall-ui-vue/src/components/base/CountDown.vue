<template>
  <span class="countdown" :class="{ 'is-urgent': urgent }">
    <span class="num">{{ parts.h }}</span>
    <span class="sep">:</span>
    <span class="num">{{ parts.m }}</span>
    <span class="sep">:</span>
    <span class="num">{{ parts.s }}</span>
  </span>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'

const props = defineProps({
  // 倒计时到的时间戳（ms）
  target: { type: Number, required: true },
})
const emit = defineEmits(['finish'])

const now = ref(Date.now())
let timer = null

onMounted(() => {
  timer = setInterval(() => {
    now.value = Date.now()
    if (now.value >= props.target) {
      clearInterval(timer)
      emit('finish')
    }
  }, 250)
})
onBeforeUnmount(() => clearInterval(timer))

const remain = computed(() => Math.max(0, props.target - now.value))
const urgent = computed(() => remain.value > 0 && remain.value <= 10000)

const parts = computed(() => {
  const t = Math.floor(remain.value / 1000)
  return {
    h: String(Math.floor(t / 3600)).padStart(2, '0'),
    m: String(Math.floor((t % 3600) / 60)).padStart(2, '0'),
    s: String(t % 60).padStart(2, '0'),
  }
})
</script>

<style scoped>
.countdown {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  font-family: var(--font-num);
  font-weight: 700;
  font-size: 22px;
  color: var(--text-strong);
}
.countdown .sep { color: var(--neon-purple); padding: 0 2px; }
.countdown.is-urgent .num { animation: countdown-urgent 1s ease-in-out infinite; }
.countdown.is-urgent .num { color: var(--seckill-from); text-shadow: 0 0 8px var(--seckill-glow); }
@keyframes countdown-urgent {
  0%, 100% { opacity: 1; transform: scale(1); }
  50%      { opacity: 0.55; transform: scale(1.08); }
}
</style>
