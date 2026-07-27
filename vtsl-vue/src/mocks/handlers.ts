import { http, HttpResponse, delay } from 'msw'
import type { ApiResponse } from '@/types/api'
import type { SeckillOrder } from '@/types/seckill'

/** MSW Handlers — 仅 DEV 生效，后端就绪后由 Vite proxy 接管（onUnhandledRequest: bypass） */

function ok<T>(data: T, message = 'success'): ApiResponse<T> {
  return { code: 200, message, data, timestamp: Date.now() }
}
function err(code: number, message: string): ApiResponse<null> {
  return { code, message, data: null, timestamp: Date.now() }
}

// ===== Mock 数据 =====
/** 角色由登录用户名决定：含 "anchor" → 2(主播)，否则 1(观众) */
let mockRole = 1
/** 主播当前活跃直播间（start/stop 维护） */
let activeRoom: Record<string, unknown> | null = null

const mockUser = {
  id: 10001,
  username: 'demo',
  nickname: '演示用户',
  avatar: '',
  phone: '13800138000',
  role: 1,
  status: 1,
}

const now = Date.now()
const mockRooms = [
  { id: 1, title: '今晚 8 点 iPhone 专场秒杀', anchorName: '科技小辛', category: '数码', coverColor: '#FF2C55', onlineCount: 12860, status: 1, startedAt: new Date(now - 3600_000).toISOString() },
  { id: 2, title: '春季新款穿搭分享', anchorName: '穿搭博主CC', category: '服饰', coverColor: '#8a63ff', onlineCount: 4521, status: 1, startedAt: new Date(now - 7200_000).toISOString() },
  { id: 3, title: '零食大礼包半价抢', anchorName: '吃货联盟', category: '美食', coverColor: '#34C759', onlineCount: 8930, status: 1, startedAt: new Date(now - 1800_000).toISOString() },
  { id: 4, title: '家电焕新季', anchorName: '家电老炮', category: '家电', coverColor: '#00e5ff', onlineCount: 2103, status: 1, startedAt: new Date(now - 5400_000).toISOString() },
  { id: 5, title: '美妆护肤专场', anchorName: '美妆七七', category: '美妆', coverColor: '#ff7ad9', onlineCount: 6642, status: 1, startedAt: new Date(now - 2700_000).toISOString() },
  { id: 6, title: '运动装备大清仓', anchorName: '健身阿凯', category: '运动', coverColor: '#ffcb55', onlineCount: 1314, status: 1, startedAt: new Date(now - 900_000).toISOString() },
]

const mockActivities = [
  {
    id: 101, title: 'AirPods Pro 2 限量秒杀', productId: 2001,
    seckillPrice: 9.9, originalPrice: 1899, totalStock: 50,
    startTime: new Date(now - 1800_000).toISOString(),
    endTime: new Date(now + 3600_000).toISOString(),
    status: 1, roomId: 1,
  },
  {
    id: 102, title: '机械键盘 87 键 RGB', productId: 2002,
    seckillPrice: 99, originalPrice: 399, totalStock: 200,
    startTime: new Date(now + 3600_000).toISOString(),
    endTime: new Date(now + 7200_000).toISOString(),
    status: 0, roomId: 1,
  },
  {
    id: 103, title: '零食大礼包 30 包', productId: 2003,
    seckillPrice: 19.9, originalPrice: 59.9, totalStock: 500,
    startTime: new Date(now - 600_000).toISOString(),
    endTime: new Date(now + 5400_000).toISOString(),
    status: 1, roomId: 3,
  },
]

const mockOrders: SeckillOrder[] = [
  { id: 1, orderNo: 'SK20260720001', activityId: 101, productId: 2001, seckillPrice: 9.9, status: 0, createdAt: new Date(now - 300_000).toISOString(), paidAt: null, cancelledAt: null },
  { id: 2, orderNo: 'SK20260719002', activityId: 103, productId: 2003, seckillPrice: 19.9, status: 1, createdAt: new Date(now - 86400_000).toISOString(), paidAt: new Date(now - 86100_000).toISOString(), cancelledAt: null },
]

// 每次请求动态读取（device_id 由首个请求拦截器懒生成，模块加载时可能还不存在）
function mockDevices() {
  return [
    { deviceId: localStorage.getItem('device_id') || 'unknown', current: true },
    { deviceId: 'a1b2c3d4-older-device-uuid', current: false },
  ]
}

const mockRankings = [
  { userId: 10086, score: 5200, rank: 1 },
  { userId: 10010, score: 3860, rank: 2 },
  { userId: 10024, score: 2100, rank: 3 },
  { userId: 10001, score: 860, rank: 4 },
  { userId: 10055, score: 420, rank: 5 },
]

export const handlers = [
  // ===== 认证 =====
  http.post('/api/auth/login', async ({ request }) => {
    await delay(400)
    const body = (await request.json().catch(() => ({}))) as { username?: string }
    mockRole = body.username?.toLowerCase().includes('anchor') ? 2 : 1
    return HttpResponse.json(ok({
      accessToken: 'mock_at_' + Date.now(),
      refreshToken: 'mock_rt_' + Date.now(),
      expiresIn: 900,
      tokenType: 'Bearer',
    }))
  }),

  http.post('/api/auth/register', async () => {
    await delay(300)
    return HttpResponse.json(ok(null, '注册成功'))
  }),

  http.post('/api/auth/refresh', async () => {
    await delay(200)
    return HttpResponse.json(ok({
      accessToken: 'mock_at_' + Date.now(),
      refreshToken: 'mock_rt_' + Date.now(),
      expiresIn: 900,
      tokenType: 'Bearer',
    }))
  }),

  http.post('/api/auth/logout', async () => {
    await delay(150)
    return HttpResponse.json(ok(null))
  }),

  // ===== 用户 =====
  http.get('/api/user/profile', async () => {
    await delay(200)
    return HttpResponse.json(ok({ ...mockUser, role: mockRole }))
  }),

  http.put('/api/user/profile', async ({ request }) => {
    await delay(200)
    const body = (await request.json()) as Record<string, string>
    return HttpResponse.json(ok({ ...mockUser, ...body }))
  }),

  http.put('/api/user/password', async () => {
    await delay(200)
    return HttpResponse.json(ok(null, '密码修改成功'))
  }),

  http.get('/api/user/balance', async () => {
    await delay(150)
    return HttpResponse.json(ok(8888))
  }),

  http.get('/api/user/devices', async () => {
    await delay(200)
    return HttpResponse.json(ok(mockDevices()))
  }),

  http.delete('/api/user/devices/:deviceId', async () => {
    await delay(200)
    return HttpResponse.json(ok(null, '设备已下线'))
  }),

  // ===== 直播间 =====
  http.get('/api/live/rooms', async () => {
    await delay(300)
    return HttpResponse.json(ok(mockRooms))
  }),

  http.get('/api/live/room/:roomId', async ({ params }) => {
    await delay(200)
    const room = mockRooms.find(r => r.id === Number(params.roomId))
    if (!room) return HttpResponse.json(err(404, '直播间不存在'))
    return HttpResponse.json(ok(room))
  }),

  // ===== B 端：开播/关播/我的活跃直播间 =====
  http.post('/api/live/room/start', async ({ request }) => {
    await delay(300)
    const body = (await request.json()) as { title: string; category?: string; coverColor?: string }
    // 幂等：已有直播中房间则复用
    if (activeRoom) return HttpResponse.json(ok(activeRoom))
    activeRoom = {
      id: 9000 + Math.floor(Math.random() * 100),
      title: body.title,
      anchorName: mockUser.nickname,
      category: body.category || 'other',
      coverColor: body.coverColor || '#8a63ff',
      onlineCount: 0,
      status: 1,
      startedAt: new Date().toISOString(),
    }
    mockRooms.unshift(activeRoom as (typeof mockRooms)[number])
    return HttpResponse.json(ok(activeRoom))
  }),

  http.post('/api/live/room/stop', async () => {
    await delay(250)
    if (activeRoom) {
      const i = mockRooms.findIndex(r => r.id === (activeRoom as { id: number }).id)
      if (i >= 0) mockRooms.splice(i, 1)
      activeRoom = null
    }
    return HttpResponse.json(ok(null))
  }),

  http.get('/api/live/my-active-room', async () => {
    await delay(150)
    return HttpResponse.json(ok(activeRoom))
  }),

  // ===== 秒杀 =====
  // 与后端对齐：传 roomId → 仅该房间进行中；不传 → 全部活动
  http.get('/api/seckill/activity/list', async ({ request }) => {
    await delay(250)
    const url = new URL(request.url)
    const roomId = url.searchParams.get('roomId')
    const list = roomId
      ? mockActivities.filter(a => a.roomId === Number(roomId) && a.status === 1)
      : mockActivities
    return HttpResponse.json(ok(list))
  }),

  http.get('/api/seckill/activity/:id', async ({ params }) => {
    await delay(200)
    const act = mockActivities.find(a => a.id === Number(params.id))
    if (!act) return HttpResponse.json(err(404, '活动不存在'))
    return HttpResponse.json(ok(act))
  }),

  // ===== B 端：创建活动 / 上下架 =====
  http.post('/api/seckill/activity', async ({ request }) => {
    await delay(300)
    const body = (await request.json()) as {
      name: string; price: number; origPrice: number; stockTotal: number
      startAt: number; endAt: number; productId?: number; roomId?: number
    }
    const act = {
      id: 100 + mockActivities.length + 1,
      title: body.name,
      productId: body.productId ?? 1001,
      seckillPrice: body.price,
      originalPrice: body.origPrice,
      totalStock: body.stockTotal,
      startTime: new Date(body.startAt).toISOString(),
      endTime: new Date(body.endAt).toISOString(),
      status: 0,
      roomId: body.roomId ?? 0,
    }
    mockActivities.push(act)
    return HttpResponse.json(ok(act))
  }),

  http.put('/api/seckill/activity/:id/status', async ({ params, request }) => {
    await delay(200)
    const body = (await request.json()) as { status: number }
    const act = mockActivities.find(a => a.id === Number(params.id))
    if (!act) return HttpResponse.json(err(404, '活动不存在'))
    act.status = body.status
    return HttpResponse.json(ok(null))
  }),

  http.post('/api/seckill/order', async () => {
    await delay(300)
    // 注意：真实环境结果由 WS SEC_KILL_RESULT 异步确认，此处仅扣库存成功
    // 与真实后端一致（Kafka Consumer 异步落单）：订单直接进入列表
    const orderNo = 'SK' + Date.now()
    mockOrders.unshift({
      id: mockOrders.length + 1,
      orderNo,
      activityId: 101,
      productId: 2001,
      seckillPrice: 9.9,
      status: 0,
      createdAt: new Date().toISOString(),
      paidAt: null,
      cancelledAt: null,
    })
    return HttpResponse.json(ok({ result: 'ok', orderNo }))
  }),

  // 注意：/list 必须注册在 /:orderNo 之前，否则 "list" 会被当作 orderNo 匹配
  http.get('/api/seckill/order/list', async () => {
    await delay(250)
    return HttpResponse.json(ok(mockOrders))
  }),

  http.get('/api/seckill/order/:orderNo', async ({ params }) => {
    await delay(150)
    const order = mockOrders.find(o => o.orderNo === params.orderNo)
    if (!order) return HttpResponse.json(err(404, '订单不存在'))
    return HttpResponse.json(ok(order))
  }),

  http.put('/api/seckill/order/:orderNo/refund', async ({ params }) => {
    await delay(250)
    const order = mockOrders.find(o => o.orderNo === params.orderNo)
    if (!order) return HttpResponse.json(err(404, '订单不存在'))
    order.status = 3
    return HttpResponse.json(ok(null, '退款成功'))
  }),

  http.put('/api/seckill/order/:orderNo/cancel', async ({ params }) => {
    await delay(200)
    const order = mockOrders.find(o => o.orderNo === params.orderNo)
    if (order) {
      order.status = 2
      order.cancelledAt = new Date().toISOString()
    }
    return HttpResponse.json(ok(null, '订单已取消'))
  }),

  // ===== 排行榜 =====
  http.get('/api/leaderboard/top', async () => {
    await delay(200)
    return HttpResponse.json(ok(mockRankings))
  }),

  http.get('/api/leaderboard/rank/:userId', async ({ params }) => {
    await delay(150)
    const entry = mockRankings.find(r => r.userId === Number(params.userId))
    return HttpResponse.json(ok(entry ?? { userId: Number(params.userId), score: 0, rank: 0 }))
  }),
]
