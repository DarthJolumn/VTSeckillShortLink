/** 创建活动请求（前端字段名 → 后端映射见联调文档 §4.7） */
export interface CreateActivityRequest {
  name: string        // → title
  price: number       // → seckillPrice
  origPrice: number   // → originalPrice
  stockTotal: number  // → totalStock
  startAt: number     // epoch ms → startTime
  endAt: number       // epoch ms → endTime
  productId?: number
  roomId?: number
}

export interface SeckillActivity {
  id: number
  title: string
  productId: number
  seckillPrice: number
  originalPrice: number
  totalStock: number
  startTime: string  // ISO LocalDateTime
  endTime: string
  status: number     // 0待开始 1进行中 2已结束 3已取消
  roomId: number
}

export interface PlaceOrderResponse {
  result: string   // "ok" 或错误
  orderNo: string
}

export interface SeckillOrder {
  id: number
  orderNo: string
  activityId: number
  productId: number
  seckillPrice: number
  status: number   // 0待支付 1已支付 2已取消 3已退款
  createdAt: string
  paidAt: string | null
  cancelledAt: string | null
}

/** 秒杀状态机 */
export type SeckillStatus =
  | 'PENDING'     // 未开始
  | 'READY'       // 即将开始
  | 'PROCESSING'  // 可抢购
  | 'QUEUING'     // 排队中（已下单等 WS 结果）
  | 'SUCCESS'     // 抢到
  | 'FAILED'      // 售罄/失败
