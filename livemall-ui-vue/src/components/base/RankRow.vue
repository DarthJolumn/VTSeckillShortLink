<template>
  <li class="rank-row" :class="[`rank-row--${tier}`, { 'is-me': isMe }]" :data-rank="rank">
    <span class="rank-row__no num">{{ rankLabel }}</span>
    <span class="rank-row__avatar">
      <img v-if="item.avatar" :src="item.avatar" alt="" />
      <span v-else>{{ (item.name || 'U').slice(0, 1) }}</span>
      <span v-if="tier" class="rank-row__crown" aria-hidden="true">{{ crown }}</span>
    </span>
    <span class="rank-row__name ellipsis">{{ item.name }}</span>
    <span class="rank-row__score num">{{ format(item.score) }}</span>
    <span v-if="trend" class="rank-row__trend" :class="`is-${trend}`">{{ trendIcon }}</span>
  </li>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  item: { type: Object, required: true },
  rank: { type: Number, required: true }, // 1-based
  prevRank: { type: Number, default: null },
  isMe: { type: Boolean, default: false },
})

const tier = computed(() => {
  if (props.rank === 1) return 'gold'
  if (props.rank === 2) return 'silver'
  if (props.rank === 3) return 'bronze'
  return ''
})
const crown = computed(() => ({ gold: '👑', silver: '🥈', bronze: '🥉' }[tier.value] || ''))
const rankLabel = computed(() => (props.rank <= 3 ? '' : props.rank))

const trend = computed(() => {
  if (props.prevRank == null) return ''
  if (props.prevRank === props.rank) return 'flat'
  if (props.prevRank > props.rank) return 'up'   // 排名数字变小=上升
  return 'down'
})
const trendIcon = computed(() => ({ up: '↑', down: '↓', flat: '—' }[trend.value] || ''))

function format(n) {
  n = Number(n || 0)
  if (n >= 10000) return (n / 10000).toFixed(1) + 'w'
  return n.toLocaleString('en-US')
}
</script>

<style scoped>
.rank-row {
  display: grid;
  grid-template-columns: 26px 32px 1fr auto 16px;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  border-radius: 10px;
  font-size: 13px;
  transition: background 0.2s, transform 0.4s var(--ease-out-expo);
}
.rank-row:hover { background: var(--bg-card); }
.rank-row__no { color: var(--text-dim); font-weight: 700; text-align: center; }
.rank-row__avatar {
  position: relative;
  width: 28px; height: 28px;
  border-radius: 50%;
  background: var(--bg-card);
  border: 1px solid var(--border-soft);
  display: grid; place-items: center;
  font-weight: 600; font-size: 12px;
  color: var(--text);
  overflow: visible;
}
.rank-row__avatar img { width: 100%; height: 100%; object-fit: cover; border-radius: 50%; }
.rank-row__crown { position: absolute; top: -10px; right: -6px; font-size: 13px; filter: drop-shadow(0 0 4px rgba(255,215,0,0.6)); }
.rank-row__name { color: var(--text); }
.rank-row__score { color: var(--text-strong); font-weight: 700; }
.rank-row__trend { font-size: 12px; }
.rank-row__trend.is-up { color: var(--success); }
.rank-row__trend.is-down { color: var(--danger); }
.rank-row__trend.is-flat { color: var(--text-dim); }

.rank-row--gold { background: linear-gradient(90deg, rgba(255,214,102,0.16), transparent 80%); border: 1px solid rgba(255,214,102,0.3); }
.rank-row--gold .rank-row__avatar { border-color: var(--rank-gold); box-shadow: 0 0 12px rgba(255,214,102,0.5); }
.rank-row--gold .rank-row__score { color: var(--rank-gold); }

.rank-row--silver { background: linear-gradient(90deg, rgba(201,205,212,0.1), transparent 80%); }
.rank-row--silver .rank-row__avatar { border-color: var(--rank-silver); box-shadow: 0 0 10px rgba(201,205,212,0.3); }
.rank-row--silver .rank-row__score { color: var(--rank-silver); }

.rank-row--bronze { background: linear-gradient(90deg, rgba(255,165,114,0.12), transparent 80%); }
.rank-row--bronze .rank-row__avatar { border-color: var(--rank-bronze); box-shadow: 0 0 10px rgba(255,165,114,0.3); }
.rank-row--bronze .rank-row__score { color: var(--rank-bronze); }

.rank-row.is-me {
  border: 1px solid var(--neon-cyan);
  box-shadow: 0 0 14px var(--neon-cyan-soft);
  background: linear-gradient(90deg, rgba(0,229,255,0.1), transparent 80%);
}
</style>
