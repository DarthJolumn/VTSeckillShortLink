import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { get, post, put } from '@/utils/http'
import { serverNow } from '@/utils/time'
import type { SeckillActivity, SeckillOrder, SeckillStatus, PlaceOrderResponse, CreateActivityRequest } from '@/types/seckill'
import type { SecKillResultData } from '@/types/ws'

/** 距离开始 ≤60s 视为 READY（即将开始） */
const READY_WINDOW_MS = 60_000
/** WS 结果等待超时（联调文档：15s+ 后兜底查单） */
const WS_RESULT_TIMEOUT_MS = 15_000

export const useSeckillStore = defineStore('seckill', () => {
  const activities = ref<SeckillActivity[]>([])
  const currentActivity = ref<SeckillActivity | null>(null)
  const orders = ref<SeckillOrder[]>([])

  /** 下单阶段（本地订单流状态，叠加在活动状态机上） */
  const orderPhase = ref<'idle' | 'queuing' | 'success' | 'failed'>('idle')

  // 秒杀状态机
  const seckillStatus = computed<SeckillStatus>(() => {
    if (orderPhase.value === 'queuing') return 'QUEUING'
    if (orderPhase.value === 'success') return 'SUCCESS'
    if (orderPhase.value === 'failed') return 'FAILED'
    const a = currentActivity.value
    if (!a) return 'PENDING'
    const start = new Date(a.startTime).getTime()
    const end = new Date(a.endTime).getTime()
    const now = serverNow()
    if (a.status === 2 || a.status === 3 || now > end) return 'FAILED'
    if (a.status === 1 && now >= start && now <= end) return 'PROCESSING'
    if (now >= start - READY_WINDOW_MS) return 'READY'
    return 'PENDING'
  })

  // 距开始倒计时（ms，服务端时间基准）
  const countdown = computed(() => {
    if (!currentActivity.value) return 0
    const start = new Date(currentActivity.value.startTime).getTime()
    return Math.max(0, start - serverNow())
  })

  // orderNo → 回调（WS SEC_KILL_RESULT 匹配，联调文档 §第三阶段）
  const orderCallbacks = new Map<string, (msg: SecKillResultData) => void>()

  function registerOrderCallback(orderNo: string, cb: (msg: SecKillResultData) => void, timeout = WS_RESULT_TIMEOUT_MS) {
    const timer = setTimeout(() => {
      orderCallbacks.delete(orderNo)
      cb({ orderNo, ok: false, reason: 'timeout', message: '结果确认超时', timestamp: Date.now() })
    }, timeout)
    orderCallbacks.set(orderNo, (msg) => { clearTimeout(timer); cb(msg) })
  }

  function resolveOrderCallback(msg: SecKillResultData) {
    const cb = orderCallbacks.get(msg.orderNo)
    if (cb) {
      cb(msg)
      orderCallbacks.delete(msg.orderNo)
    }
  }

  function resetOrderPhase() {
    orderPhase.value = 'idle'
  }

  /**
   * 活动列表。⚠️ 后端当前实现只返回 status=1（进行中）的活动
   * （SeckillService.getActivities → findByStatus(1)），
   * B 端管理页需要全量状态是后端待补能力，Mock 按全量返回。
   */
  async function fetchActivities(roomId?: number) {
    console.log('[seckillStore] fetchActivities 请求发出, roomId:', roomId)
    const res = await get<SeckillActivity[]>('/seckill/activity/list', {
      params: roomId ? { roomId } : {},
    })
    console.log('[seckillStore] fetchActivities 响应:', JSON.stringify(res).slice(0, 300))
    activities.value = res.data
    return activities.value.length
  }

  /** 创建活动（B 端）。字段名即后端 DTO 字段：name/price/origPrice/stockTotal/startAt/endAt */
  async function createActivity(req: CreateActivityRequest) {
    const res = await post<SeckillActivity>('/seckill/activity', req)
    activities.value.push(res.data)
    return res.data
  }

  /** 上架(→1) / 下架(→3) */
  async function updateActivityStatus(id: number, status: number) {
    await put(`/seckill/activity/${id}/status`, { status })
    const target = activities.value.find(a => a.id === id)
    if (target) target.status = status
  }

  /**
   * 下单 + 等待 WS 异步确认闭环：
   * 1. POST 扣库存 → result:ok 仅表示排队中
   * 2. 注册 orderNo 回调，等 SEC_KILL_RESULT（15s 超时）
   */
  async function placeOrder(activityId: number): Promise<SecKillResultData> {
    const res = await post<PlaceOrderResponse>('/seckill/order', { activityId })
    if (res.data.result !== 'ok') {
      throw new Error(res.data.result || '下单失败')
    }
    const orderNo = res.data.orderNo
    orderPhase.value = 'queuing'
    return new Promise<SecKillResultData>((resolve, reject) => {
      registerOrderCallback(orderNo, (msg) => {
        if (msg.ok) {
          orderPhase.value = 'success'
          resolve(msg)
        } else {
          orderPhase.value = 'failed'
          reject(new Error(msg.message || msg.reason || '抢购失败'))
        }
      })
    })
  }

  async function fetchOrders() {
    const res = await get<SeckillOrder[]>('/seckill/order/list')
    orders.value = res.data
  }

  async function fetchOrderDetail(orderNo: string): Promise<SeckillOrder> {
    const res = await get<SeckillOrder>(`/seckill/order/${orderNo}`)
    return res.data
  }

  async function cancelOrder(orderNo: string) {
    await put(`/seckill/order/${orderNo}/cancel`)
    await fetchOrders()
  }

  /** 申请退款（已支付订单，status 1 → 3） */
  async function refundOrder(orderNo: string) {
    await put(`/seckill/order/${orderNo}/refund`)
    await fetchOrders()
  }

  return { activities, currentActivity, orders, orderPhase, seckillStatus, countdown,
           registerOrderCallback, resolveOrderCallback, resetOrderPhase,
           fetchActivities, createActivity, updateActivityStatus,
           placeOrder, fetchOrders, fetchOrderDetail, cancelOrder, refundOrder }
})
