// 排行榜服务接口（/leaderboard） · 对应后端 2.8 接口文档 §四
import http from '@/infra/request'

export const leaderboardApi = {
  topN(params) { return http.get('/leaderboard/top', { params }) },
  rank(userId, params) { return http.get(`/leaderboard/rank/${userId}`, { params }) },
  history(params) { return http.get('/leaderboard/history', { params }) },
}
