// 全局常量 · 角色码 / 订单状态 / WS 消息类型 / 事件权重
// 来源：md/docs 1.3-全功能清单 / 2.4-WebSocket / 2.5-排行榜 / 2.7-数据库设计

export const ROLE = Object.freeze({
  AUDIENCE: 1, // 观众
  ANCHOR: 2,   // 主播
  ADMIN: 3,    // 管理员
})

export const ROLE_LABEL = {
  [ROLE.AUDIENCE]: '观众',
  [ROLE.ANCHOR]: '主播',
  [ROLE.ADMIN]: '管理员',
}

// 订单状态 t_seckill_order.status
export const ORDER_STATUS = Object.freeze({
  PENDING: 0,    // 待支付
  PAID: 1,       // 已支付
  CANCELLED: 2,  // 已取消
  REFUNDED: 3,   // 已退款
})

export const ORDER_STATUS_LABEL = {
  [ORDER_STATUS.PENDING]: '待支付',
  [ORDER_STATUS.PAID]: '已支付',
  [ORDER_STATUS.CANCELLED]: '已取消',
  [ORDER_STATUS.REFUNDED]: '已退款',
}

// 活动状态 t_seckill_activity.status
export const ACTIVITY_STATUS = Object.freeze({
  PENDING: 0,   // 待开始
  RUNNING: 1,   // 进行中
  ENDED: 2,     // 已结束
  CANCELLED: 3, // 已取消
})

// 直播间状态 t_live_room.status
export const ROOM_STATUS = Object.freeze({
  OFFLINE: 0,
  LIVE: 1,
})

// WebSocket 消息类型（与后端 2.8 接口文档 / 2.4 消息协议一致）
export const WS_TYPE = Object.freeze({
  // 上行
  PING: 'PING',
  BARRAGE: 'BARRAGE',
  GIFT: 'GIFT',
  SEC_KILL: 'SEC_KILL',
  // 下行
  PONG: 'PONG',
  BARRAGE_DOWN: 'BARRAGE',
  GIFT_DOWN: 'GIFT',
  SEC_KILL_RESULT: 'SEC_KILL_RESULT',
  KICK: 'KICK',
  ONLINE_COUNT: 'ONLINE_COUNT',
  BAN: 'BAN',
  ROOM_CLOSED: 'ROOM_CLOSED',
})

// 排行榜事件权重（来源 2.5-排行榜服务 §权重配置表）
export const SCORE_WEIGHT = Object.freeze({
  WATCH: 0.3,
  LIKE: 0.5,
  COMMENT: 1.0,
  SHARE: 2.0,
  GIFT: 1.0, // 实际 = giftPrice * quantity * 1.0
})

// 订单超时（与后端 2.3 超时取消 @Scheduled 15min 对齐）
export const ORDER_TTL_MS = 15 * 60 * 1000

// WS 心跳与超时（与后端 2.4 心跳对齐：客户端 30s PING，服务端 60s 超时）
export const WS_HEARTBEAT_MS = 30 * 1000
export const WS_PONG_TIMEOUT_MS = 60 * 1000
export const WS_RECONNECT_MAX = 10

// LocalStorage / SessionStorage keys
export const STORAGE_KEY = Object.freeze({
  ACCESS: 'lm_access',
  REFRESH: 'lm_refresh',
  USER: 'lm_user',
})
