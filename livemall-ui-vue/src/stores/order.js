// 订单状态 · 与 constants ORDER_STATUS 对齐
import { ORDER_STATUS, ORDER_TTL_MS } from '@/constants'

const LS_KEY = 'lm_orders'

function now() { return Date.now() }

function load() {
  try {
    const raw = localStorage.getItem(LS_KEY)
    if (!raw) return []
    const arr = JSON.parse(raw)
    return Array.isArray(arr) ? arr : []
  } catch { return [] }
}

function save(orders) {
  try { localStorage.setItem(LS_KEY, JSON.stringify(orders)) } catch { /* ignore */ }
}

// 雪花式订单号（演示用）
function genOrderNo() {
  const t = Date.now().toString(36)
  const r = Math.random().toString(36).slice(2, 8)
  return `LM${t}${r}`.toUpperCase()
}

export const orderStore = {
  _orders: null,
  _timer: null,

  _ensure() {
    if (this._orders == null) this._orders = load()
    return this._orders
  },
  _persist() {
    save(this._orders)
  },

  // 启动超时巡检：每 5s 扫一次，待支付订单超 15min 自动取消
  startTimeoutWatch() {
    if (this._timer) return
    this._timer = setInterval(() => this._sweepTimeout(), 5000)
    this._sweepTimeout() // 立即执行一次
  },
  _sweepTimeout() {
    const arr = this._ensure()
    let changed = false
    const t = now()
    for (const o of arr) {
      if (o.status === ORDER_STATUS.PENDING && t - o.createdAt > ORDER_TTL_MS) {
        o.status = ORDER_STATUS.CANCELLED
        o.cancelledAt = t
        o.cancelReason = '超时未支付，系统自动取消'
        changed = true
      }
    }
    if (changed) this._persist()
  },

  list(filter = {}) {
    const arr = this._ensure()
    let res = [...arr].sort((a, b) => b.createdAt - a.createdAt)
    if (filter.status != null && filter.status !== 'all') {
      res = res.filter((o) => o.status === Number(filter.status))
    }
    return res
  },

  detail(orderNo) {
    return this._ensure().find((o) => o.orderNo === orderNo) || null
  },

  // 创建订单（抢购成功时调用）
  create(activity) {
    const arr = this._ensure()
    const o = {
      orderNo: genOrderNo(),
      activityId: activity.id,
      activityName: activity.name,
      price: activity.price,
      origPrice: activity.origPrice,
      quantity: 1,
      status: ORDER_STATUS.PENDING,
      createdAt: now(),
      expireAt: now() + ORDER_TTL_MS,
    }
    arr.unshift(o)
    this._persist()
    return o
  },

  // 支付（mock）
  pay(orderNo) {
    const o = this.detail(orderNo)
    if (!o) return false
    if (o.status !== ORDER_STATUS.PENDING) return false
    o.status = ORDER_STATUS.PAID
    o.paidAt = now()
    this._persist()
    return true
  },

  // 取消
  cancel(orderNo, reason = '用户主动取消') {
    const o = this.detail(orderNo)
    if (!o) return false
    if (o.status !== ORDER_STATUS.PENDING) return false
    o.status = ORDER_STATUS.CANCELLED
    o.cancelledAt = now()
    o.cancelReason = reason
    this._persist()
    return true
  },

  // 退款（已支付才能退）
  refund(orderNo) {
    const o = this.detail(orderNo)
    if (!o) return false
    if (o.status !== ORDER_STATUS.PAID) return false
    o.status = ORDER_STATUS.REFUNDED
    o.refundedAt = now()
    this._persist()
    return true
  },

  // 清空（演示用）
  clear() {
    this._orders = []
    this._persist()
  },
}

// 剩余支付时间（ms），<=0 返回 0
export function remainMs(order) {
  if (!order || order.status !== ORDER_STATUS.PENDING) return 0
  return Math.max(0, order.expireAt - now())
}
