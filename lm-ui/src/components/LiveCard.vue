<template>
  <div class="live-card" @click="emit('click', room.id)">
    <!-- 封面（色块 + 首字符，真实场景替换为 coverUrl 图片） -->
    <div class="cover" :style="{ background: `linear-gradient(135deg, ${room.coverColor}33, ${room.coverColor}11)` }">
      <span class="cover-letter" :style="{ color: room.coverColor }">{{ room.title.charAt(0) }}</span>

      <span v-if="room.status === 1" class="badge-live">
        <span class="dot" />直播中
      </span>
      <span v-else class="badge-end">已结束</span>

      <span v-if="hasSeckill" class="badge-seckill">秒杀中</span>

      <div class="info-mask">
        <div class="title">{{ room.title }}</div>
        <div class="meta">
          <span class="anchor">{{ room.anchorName }}</span>
          <span class="online">👁 {{ formatCount(room.onlineCount) }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { RoomVO } from '@/types/live'

defineProps<{ room: RoomVO; hasSeckill?: boolean }>()
const emit = defineEmits<{ click: [roomId: number] }>()

function formatCount(n: number): string {
  return n >= 10000 ? (n / 10000).toFixed(1) + 'w' : String(n)
}
</script>

<style scoped>
.live-card {
  border-radius: 12px;
  overflow: hidden;
  cursor: pointer;
  transition: transform 0.2s, box-shadow 0.2s;
  border: 1px solid var(--border);
}
.live-card:hover {
  transform: scale(1.02);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.5);
}
.cover {
  position: relative;
  aspect-ratio: 16 / 10;
  display: flex;
  align-items: center;
  justify-content: center;
}
.cover-letter {
  font-size: 56px;
  font-weight: 700;
  opacity: 0.5;
}

.badge-live, .badge-end, .badge-seckill {
  position: absolute;
  font-size: 11px;
  padding: 3px 8px;
  border-radius: 4px;
  font-weight: 600;
}
.badge-live {
  top: 10px;
  left: 10px;
  background: rgba(0, 0, 0, 0.6);
  color: var(--status-green);
  display: flex;
  align-items: center;
  gap: 4px;
}
.badge-live .dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--status-green);
  animation: blink 1.2s infinite;
}
.badge-end { top: 10px; left: 10px; background: rgba(0, 0, 0, 0.6); color: var(--text-secondary); }
.badge-seckill {
  top: 10px;
  right: 10px;
  background: var(--accent-red);
  color: #fff;
  animation: flash 1s infinite alternate;
}
@keyframes blink { 50% { opacity: 0.3; } }
@keyframes flash { from { opacity: 1; } to { opacity: 0.55; } }

.info-mask {
  position: absolute;
  inset: auto 0 0 0;
  padding: 24px 12px 10px;
  background: linear-gradient(transparent, rgba(0, 0, 0, 0.85));
}
.title {
  font-size: 14px;
  font-weight: 600;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.meta {
  display: flex;
  justify-content: space-between;
  margin-top: 4px;
  font-size: 12px;
  color: var(--text-secondary);
}
</style>
