/** WebSocket 消息协议（docs/前端/前端类型定义与状态设计.md §6） */

// ===== 服务端 → 客户端 =====
export interface WsConnected {
  type: 'CONNECTED'
  data: { anonymous: boolean; displayName: string; online: number }
}
export interface WsAuthOk {
  type: 'AUTH_OK'
  data: { userId: number; role: number; displayName: string }
}
export interface WsAuthFailed {
  type: 'AUTH_FAILED'
  data: { reason: string }
}
export interface WsNeedAuth {
  type: 'NEED_AUTH'
  data: { reason: string }
}
export interface WsPong {
  type: 'PONG'
  data: Record<string, never>
}
export interface WsBarrage {
  type: 'BARRAGE'
  data: { userId: number; username: string; avatar: string; content: string; timestamp: number }
}
export interface WsGift {
  type: 'GIFT'
  data: {
    userId: number; username: string; giftId: number; giftName: string
    giftIcon: string; price: number; gain: number; quantity: number; timestamp: number
  }
}
export interface SecKillResultData {
  orderNo: string
  ok: boolean
  reason: string
  message: string
  timestamp: number
}
export interface WsSecKillResult {
  type: 'SEC_KILL_RESULT'
  data: SecKillResultData
}
export interface WsRoomClosed {
  type: 'ROOM_CLOSED'
  data: { roomId: number }
}
export interface WsKick {
  type: 'KICK'
  data: { reason: string }
}
export interface WsError {
  type: 'ERROR'
  data: { reason: string }
}

export type WsServerMessage =
  | WsConnected | WsAuthOk | WsAuthFailed | WsNeedAuth
  | WsPong | WsBarrage | WsGift | WsSecKillResult
  | WsRoomClosed | WsKick | WsError

// ===== 客户端 → 服务端 =====
export interface WsPing { type: 'PING' }
export interface WsAuth { type: 'AUTH'; data: { token: string } }
export interface WsSendBarrage { type: 'BARRAGE'; data: { content: string } }
export interface WsSendGift { type: 'GIFT'; data: { giftId: number; quantity: number } }
export interface WsSeckill { type: 'SEC_KILL'; data: { activityId: number } }

export type WsClientMessage = WsPing | WsAuth | WsSendBarrage | WsSendGift | WsSeckill

/** 聊天面板统一消息项（弹幕 + 礼物混合列表） */
export type ChatItem =
  | ({ kind: 'barrage' } & WsBarrage['data'])
  | ({ kind: 'gift' } & WsGift['data'])
