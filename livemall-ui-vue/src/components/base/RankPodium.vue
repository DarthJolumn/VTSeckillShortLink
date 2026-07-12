<template>
  <div class="podium">
    <!-- 2nd -->
    <div v-if="items[1]" class="podium__cell podium__cell--2" :class="{ 'is-me': items[1].id === 0 }">
      <div class="podium__avatar"><span>{{ (items[1].name || 'U').slice(0,1) }}</span><i class="podium__crown">🥈</i></div>
      <div class="podium__name ellipsis">{{ items[1].name }}</div>
      <div class="podium__score num">{{ fmt(items[1].score) }}</div>
      <div class="podium__stand podium__stand--2" />
    </div>
    <!-- 1st -->
    <div v-if="items[0]" class="podium__cell podium__cell--1" :class="{ 'is-me': items[0].id === 0 }">
      <div class="podium__avatar"><span>{{ (items[0].name || 'U').slice(0,1) }}</span><i class="podium__crown">👑</i></div>
      <div class="podium__name ellipsis">{{ items[0].name }}</div>
      <div class="podium__score num">{{ fmt(items[0].score) }}</div>
      <div class="podium__stand podium__stand--1" />
    </div>
    <!-- 3rd -->
    <div v-if="items[2]" class="podium__cell podium__cell--3" :class="{ 'is-me': items[2].id === 0 }">
      <div class="podium__avatar"><span>{{ (items[2].name || 'U').slice(0,1) }}</span><i class="podium__crown">🥉</i></div>
      <div class="podium__name ellipsis">{{ items[2].name }}</div>
      <div class="podium__score num">{{ fmt(items[2].score) }}</div>
      <div class="podium__stand podium__stand--3" />
    </div>
  </div>
</template>

<script setup>
defineProps({
  items: { type: Array, default: () => [] }, // 已排序的前 3
})
function fmt(n) {
  n = Number(n || 0)
  if (n >= 10000) return (n / 10000).toFixed(1) + 'w'
  return n.toLocaleString('en-US')
}
</script>

<style scoped>
.podium {
  display: grid;
  grid-template-columns: 1fr 1.15fr 1fr;
  gap: 8px;
  align-items: end;
  padding: 8px 4px 0;
  margin-bottom: 8px;
}
.podium__cell {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  animation: float-up 0.5s var(--ease-out-expo) both;
}
.podium__cell--2 { animation-delay: 0.1s; }
.podium__cell--1 { animation-delay: 0.2s; }
.podium__cell--3 { animation-delay: 0.3s; }

.podium__avatar {
  position: relative;
  width: 44px; height: 44px;
  border-radius: 50%;
  display: grid; place-items: center;
  background: var(--bg-card);
  border: 2px solid var(--border-soft);
  font-family: var(--font-display);
  font-weight: 700;
  color: var(--text);
  font-size: 16px;
}
.podium__crown {
  position: absolute;
  top: -14px;
  font-size: 16px;
  font-style: normal;
  filter: drop-shadow(0 0 6px rgba(255,215,0,0.6));
  animation: biggift-bob 2s ease-in-out infinite;
}
.podium__name { font-size: 12px; color: var(--text); max-width: 80px; text-align: center; }
.podium__score { font-size: 13px; font-weight: 700; }

/* 领奖台柱体 */
.podium__stand {
  width: 100%;
  border-radius: 6px 6px 0 0;
  position: relative;
  overflow: hidden;
}
.podium__stand::before {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(180deg, transparent, rgba(255,255,255,0.08));
}
.podium__stand--1 { height: 56px; background: linear-gradient(180deg, rgba(255,214,102,0.5), rgba(255,214,102,0.15)); border: 1px solid rgba(255,214,102,0.5); box-shadow: 0 0 16px rgba(255,214,102,0.3); }
.podium__stand--2 { height: 40px; background: linear-gradient(180deg, rgba(201,205,212,0.4), rgba(201,205,212,0.12)); border: 1px solid rgba(201,205,212,0.4); }
.podium__stand--3 { height: 30px; background: linear-gradient(180deg, rgba(255,165,114,0.4), rgba(255,165,114,0.12)); border: 1px solid rgba(255,165,114,0.4); }

/* 冠军特效 */
.podium__cell--1 .podium__avatar { border-color: var(--rank-gold); box-shadow: 0 0 18px rgba(255,214,102,0.6); color: var(--rank-gold); }
.podium__cell--1 .podium__score { color: var(--rank-gold); }
.podium__cell--2 .podium__avatar { border-color: var(--rank-silver); box-shadow: 0 0 12px rgba(201,205,212,0.4); color: var(--rank-silver); }
.podium__cell--2 .podium__score { color: var(--rank-silver); }
.podium__cell--3 .podium__avatar { border-color: var(--rank-bronze); box-shadow: 0 0 12px rgba(255,165,114,0.4); color: var(--rank-bronze); }
.podium__cell--3 .podium__score { color: var(--rank-bronze); }

.podium__cell.is-me .podium__avatar {
  border-color: var(--neon-cyan);
  box-shadow: 0 0 18px var(--neon-cyan-soft);
}
.podium__cell.is-me::after {
  content: 'ME';
  position: absolute;
  top: -2px;
  font-family: var(--font-num);
  font-size: 9px;
  letter-spacing: 0.2em;
  color: var(--neon-cyan);
}

/* 冠军柱体扫描线 */
.podium__stand--1::after {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(180deg, transparent, rgba(255,214,102,0.4), transparent);
  height: 30%;
  animation: podium-scan 2.4s linear infinite;
}
@keyframes podium-scan {
  0% { transform: translateY(-100%); }
  100% { transform: translateY(330%); }
}
</style>
