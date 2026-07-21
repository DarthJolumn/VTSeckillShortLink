import { defineStore } from 'pinia'
import { ref } from 'vue'
import { get } from '@/utils/http'
import type { RankEntry } from '@/types/leaderboard'

export const useLeaderboardStore = defineStore('leaderboard', () => {
  const rankings = ref<RankEntry[]>([])
  const myRank = ref<RankEntry | null>(null)

  async function fetchTopN(activityId: number, n = 100) {
    const res = await get<RankEntry[]>('/leaderboard/top', { params: { activityId, n } })
    rankings.value = res.data
  }

  async function fetchMyRank(activityId: number, userId: number) {
    const res = await get<RankEntry>(`/leaderboard/rank/${userId}`, { params: { activityId } })
    myRank.value = res.data
  }

  return { rankings, myRank, fetchTopN, fetchMyRank }
})
