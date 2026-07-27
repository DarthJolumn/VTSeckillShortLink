<template>
  <div class="lb-tab">
    <div v-if="myRank" class="my-rank">
      我的排名：{{ myRank.rank > 0 ? `第 ${myRank.rank} 名` : '未上榜' }}（{{ myRank.score }} 贡献值）
    </div>
    <div v-if="rankings.length === 0" class="empty">暂无排行数据</div>
    <div v-for="entry in rankings" :key="entry.userId" class="rank-row" :class="rankClass(entry.rank)">
      <span class="rank-no">
        <template v-if="entry.rank <= 3">{{ ['🥇', '🥈', '🥉'][entry.rank - 1] }}</template>
        <template v-else>{{ entry.rank }}</template>
      </span>
      <span class="rank-user">用户 {{ entry.userId }}</span>
      <span class="rank-score">{{ entry.score }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { RankEntry } from '@/types/leaderboard'

defineProps<{ rankings: RankEntry[]; myRank?: RankEntry | null }>()

function rankClass(rank: number) {
  return rank <= 3 ? `top-${rank}` : ''
}
</script>

<style scoped>
.lb-tab { padding: 8px; overflow-y: auto; height: 100%; }
.my-rank {
  font-size: 12px;
  color: var(--accent-gold);
  padding: 8px 10px;
  margin-bottom: 8px;
  background: rgba(255, 214, 102, 0.08);
  border-radius: 8px;
}
.empty { text-align: center; color: var(--text-secondary); font-size: 13px; padding: 40px 0; }
.rank-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  border-radius: 8px;
  font-size: 13px;
}
.rank-row + .rank-row { margin-top: 2px; }
.rank-row.top-1 { background: rgba(255, 214, 102, 0.1); }
.rank-row.top-2 { background: rgba(192, 192, 192, 0.08); }
.rank-row.top-3 { background: rgba(205, 127, 50, 0.08); }
.rank-no { width: 28px; text-align: center; font-family: var(--font-mono); }
.rank-user { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.rank-score { color: var(--accent-gold); font-family: var(--font-mono); font-size: 12px; }
</style>
