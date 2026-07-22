<template>
  <div class="barrage" ref="root">
    <div
      v-for="item in visible"
      :key="item._key"
      class="barrage__item"
      :class="{ 'is-gift': item.isGift }"
      :style="{
        top: item._lane * LANE_HEIGHT + 'px',
        animationDuration: item._dur + 's',
        animationDelay: item._delay + 's',
      }"
      @animationend="onEnd(item._key)"
    >
      <span v-if="item.isGift" class="barrage__gift-icon">{{ item.giftIcon }}</span>
      <span class="barrage__user" :style="{ color: item.color }">{{ item.username }}</span>
      <span class="barrage__content">{{ item.content || giftText(item) }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onBeforeUnmount } from 'vue'
import type { ChatItem } from '@/types/ws'

const props = withDefaults(defineProps<{
  messages: ChatItem[]
  lanes?: number
}>(), {
  lanes: 6,
})

const LANE_HEIGHT = 30
const root = ref<HTMLElement>()

interface TrackItem {
  _key: string
  _lane: number
  _dur: number
  _delay: number
  color: string
  isGift: boolean
  kind?: string
  userId?: number
  username?: string
  content?: string
  giftName?: string
  giftIcon?: string
  giftId?: number
  quantity?: number
  timestamp?: number
}

const visible = ref<TrackItem[]>([])
let laneNextFreeAt: number[] = new Array(props.lanes).fill(0)
let seq = 0
let lastSeenIdx = -1

const COLORS = ['#8a63ff', '#00e5ff', '#ff7ad9', '#52e5a4', '#ffcb55', '#4cc9f0']

function giftText(item: TrackItem) {
  return item.giftName ? ` 送出 ${item.giftName} ×${item.quantity ?? 1}` : ''
}

function pickLane(nowSec: number) {
  let best = 0
  let bestAt = Infinity
  for (let i = 0; i < props.lanes; i++) {
    if (laneNextFreeAt[i] < bestAt) {
      bestAt = laneNextFreeAt[i]
      best = i
    }
  }
  return { lane: best, enterAt: Math.max(laneNextFreeAt[best], nowSec) }
}

function scheduleOne(m: ChatItem, idx: number) {
  const nowSec = performance.now() / 1000
  const { lane, enterAt } = pickLane(nowSec)
  const textLen = (('content' in m ? (m.content as string)?.length : 6) || 6) + ('giftName' in m ? 8 : 0)
  const dur = 8 + (textLen % 6) * 0.6
  laneNextFreeAt[lane] = enterAt + dur * 0.6
  const item: TrackItem = {
    ...m,
    _key: `${m.timestamp}-${idx}-${seq++}`,
    _lane: lane,
    _dur: dur,
    _delay: Math.max(0, enterAt - nowSec),
    color: COLORS[(m.userId ?? 0) % COLORS.length] || '#e5e6f0',
    isGift: 'giftName' in m,
  }
  visible.value.push(item)
}

function onEnd(key: string) {
  const arr = visible.value
  const i = arr.findIndex(x => x._key === key)
  if (i >= 0) arr.splice(i, 1)
}

watch(
  () => props.messages.length,
  (len) => {
    if (len <= lastSeenIdx) {
      visible.value = []
      laneNextFreeAt = new Array(props.lanes).fill(0)
      lastSeenIdx = len - 1
      return
    }
    for (let i = lastSeenIdx + 1; i < len; i++) {
      scheduleOne(props.messages[i], i)
    }
    lastSeenIdx = len - 1
  },
  { flush: 'post' },
)

onBeforeUnmount(() => {
  visible.value = []
  lastSeenIdx = -1
  laneNextFreeAt = new Array(props.lanes).fill(0)
})
</script>

<style scoped>
.barrage {
  position: absolute;
  inset: 0;
  overflow: hidden;
  pointer-events: none;
  z-index: 10;
}
.barrage__item {
  position: absolute;
  left: 100%;
  white-space: nowrap;
  padding: 4px 12px;
  border-radius: 999px;
  background: rgba(7, 8, 26, 0.7);
  font-size: 14px;
  color: var(--text-primary);
  animation-name: barrage-flow;
  animation-timing-function: linear;
  animation-fill-mode: forwards;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  will-change: transform;
}
.barrage__user { font-weight: 600; }
.barrage__content { color: var(--text-secondary); }
.barrage__item.is-gift {
  background: linear-gradient(90deg, rgba(255, 138, 0, 0.35), rgba(255, 77, 79, 0.3));
  border: 1px solid rgba(255, 200, 100, 0.5);
  box-shadow: 0 0 12px rgba(255, 138, 0, 0.4);
}
.barrage__gift-icon { font-size: 16px; }

@keyframes barrage-flow {
  from { transform: translateX(0); }
  to { transform: translateX(calc(-100vw - 100%)); }
}
@media (prefers-reduced-motion: reduce) {
  .barrage__item { animation: none; opacity: 0.9; }
}
</style>
