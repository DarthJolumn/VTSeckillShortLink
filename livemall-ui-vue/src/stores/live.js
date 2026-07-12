// 直播间状态 · 房间 / 在线 / 弹幕队列（环形缓冲）/ 礼物 / 排行榜 / 连接状态
import { defineStore } from 'pinia'
import { WsClient, WS_STATUS } from '@/infra/ws-client'
import { WS_TYPE } from '@/constants'

const BARRAGE_MAX = 80 // 环形缓冲上限

// mock 开关：后端未就绪时默认走 mock，让直播间动效闭环可演示
const USE_MOCK = import.meta.env.VITE_USE_WS_MOCK === '1' || !tokensExists()

function tokensExists() {
  try { return !!localStorage.getItem('lm_refresh') } catch { return false }
}

export const useLiveStore = defineStore('live', {
  state: () => ({
    roomId: null,
    status: WS_STATUS.IDLE,           // 连接状态
    online: 0,
    barrage: [],                      // 弹幕队列
    giftFeed: [],                     // 礼物流（用于特效/侧栏）
    leaderboard: [],                  // [{id,name,score}]
    myRank: null,
    stock: 0,
    stockTotal: 0,
    secKillResult: null,              // 最近一次抢购结果
    kicked: null,                     // { reason }
    _client: null,
    _unsubs: [],
  }),

  getters: {
    connected: (s) => s.status === WS_STATUS.OPEN,
    reconnecting: (s) => s.status === WS_STATUS.RECONNECTING,
    stockPct: (s) => (s.stockTotal ? Math.round((s.stock / s.stockTotal) * 100) : 0),
  },

  actions: {
    join(roomId) {
      this.leave()
      this.roomId = roomId
      this.barrage = []
      this.giftFeed = []
      this.leaderboard = []
      this.online = 0
      this.secKillResult = null // 跨房间重置，避免上一场结果残留

      this._client = new WsClient({
        roomId,
        mock: USE_MOCK,
        onStatus: (s) => { this.status = s },
        onOpen: () => {
          // mock 模式下读取快照初始化（用公共 API，不访问私有字段）
          const snap = this._client.getSnapshot?.()
          if (snap) {
            this.online = snap.online
            this.leaderboard = snap.scores
            this.stock = snap.stock
            this.stockTotal = snap.stockTotal
          }
        },
      })

      // 订阅下行消息
      const sub = (type, fn) => this._unsubs.push(this._client.on(type, fn))
      sub(WS_TYPE.BARRAGE_DOWN, (d) => this.pushBarrage(d))
      sub(WS_TYPE.GIFT_DOWN, (d) => this.onGift(d))
      sub(WS_TYPE.ONLINE_COUNT, (d) => { this.online = d.count })
      sub('STOCK_UPDATE', (d) => { this.stock = d.stock; this.stockTotal = d.total })
      sub(WS_TYPE.SEC_KILL_RESULT, (d) => { this.secKillResult = d })
      sub(WS_TYPE.KICK, (d) => { this.kicked = d || { reason: '同一账号在别处登录' } })
      sub(WS_TYPE.BAN, () => { this.kicked = { reason: '账号已被封禁', ban: true } })
      sub(WS_TYPE.ROOM_CLOSED, () => { this.kicked = { reason: '主播已下播', closed: true } })

      this._client.connect()
    },

    pushBarrage(d) {
      this.barrage.push(d)
      if (this.barrage.length > BARRAGE_MAX) this.barrage.splice(0, this.barrage.length - BARRAGE_MAX)
    },

    onGift(d) {
      this.giftFeed.unshift(d)
      if (this.giftFeed.length > 20) this.giftFeed.pop()
      // 加分并重排
      const exists = this.leaderboard.find((u) => u.id === d.userId)
      if (exists) {
        exists.score += d.gain || 0
      } else if (d.userId != null) {
        this.leaderboard.push({ id: d.userId, name: d.username, score: d.gain || 0 })
      }
      this.leaderboard.sort((a, b) => b.score - a.score)
      if (this.leaderboard.length > 50) this.leaderboard.length = 50
    },

    sendBarrage(content) {
      if (!content?.trim() || !this.connected) return false
      this._client.send(WS_TYPE.BARRAGE, { content: content.trim() })
      return true
    },

    sendGift(giftId, quantity = 1) {
      if (!this.connected) return false
      this._client.send(WS_TYPE.GIFT, { giftId, quantity })
      return true
    },

    // 上行秒杀请求；结果由 SEC_KILL_RESULT 推送回 state.secKillResult
    // reqId 用于关联请求与响应（避免错位）
    sendSeckill(activityId) {
      if (!this.connected) return null
      const reqId = crypto?.randomUUID?.() || `${Date.now()}-${Math.random()}`
      this._client.send(WS_TYPE.SEC_KILL, { activityId, reqId })
      return reqId
    },

    leave() {
      this._unsubs.forEach((fn) => fn?.())
      this._unsubs = []
      this._client?.destroy()
      this._client = null
      this.status = WS_STATUS.IDLE
      this.kicked = null
    },
  },
})
