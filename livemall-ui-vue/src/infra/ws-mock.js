// WS mock 驱动 · 无后端时模拟服务端推送，让直播间动效闭环可演示
// 仅在 WsClient mock=true 时启用；后端就绪后无需改动业务代码。

import { WS_TYPE } from '@/constants'

const BARRAGE_POOL = [
  '主播好厉害', '666', '冲冲冲', '已下单', '这个价格绝了', '再来一个',
  '蹲点抢购', '主播声音好听', '库存还有吗', '气氛组就位', '火钳刘明',
  '感谢主播', '比双十一还便宜', '冲了冲了', '求上架', '太顶了',
  '抢到啦！', '没抢到QAQ', '下一场几点', '这画质可以',
  '冲鸭冲鸭', '比心比心', '主播再演示下', '已收藏', '已加购物车',
  '这个能开发票吗', '物流几天到', '质保多久', '颜色绝了', '质感拉满',
  '买它买它', '我也想要', '蹲一个链接', '比专柜便宜一半', '主播口条顺',
  '老粉来了', '送火箭啦', '气氛起来', '价格屠夫', '真香警告',
  '主播求@我', '抽个奖呗', '抢到了谢谢', '比李佳琦还猛', '库存秒空',
]

const USERS = [
  { id: 101, name: '夜行猫', avatar: '' },
  { id: 102, name: '霓虹海', avatar: '' },
  { id: 103, name: '电光火石', avatar: '' },
  { id: 104, name: '量子土豆', avatar: '' },
  { id: 105, name: '赛博烤面', avatar: '' },
  { id: 106, name: '极地星轨', avatar: '' },
  { id: 107, name: '雾里看花', avatar: '' },
  { id: 108, name: '蒸汽少年', avatar: '' },
]

const GIFTS = [
  { id: 1, name: '玫瑰', price: 9, icon: '🌹' },
  { id: 2, name: '跑车', price: 120, icon: '🏎️' },
  { id: 3, name: '火箭', price: 666, icon: '🚀' },
  { id: 4, name: '皇冠', price: 188, icon: '👑' },
]

function pick(arr) { return arr[Math.floor(Math.random() * arr.length)] }
function randInt(a, b) { return Math.floor(a + Math.random() * (b - a + 1)) }

export function createMockDriver({ roomId, send }) {
  let timers = []
  let online = 8640
  let scores = USERS.map((u) => ({ ...u, score: randInt(800, 5200) }))
    .sort((a, b) => b.score - a.score)
  let stock = 480
  let stockTotal = 500

  function loop(fn, ms) {
    const t = setInterval(fn, ms)
    timers.push(t)
  }

  function start() {
    // 弹幕：1.2 ~ 2.5s 一条
    loop(() => {
      const u = pick(USERS)
      send(WS_TYPE.BARRAGE_DOWN, {
        userId: u.id,
        username: u.name,
        avatar: u.avatar,
        content: pick(BARRAGE_POOL),
        timestamp: Date.now(),
      })
    }, 1600)

    // 礼物：5 ~ 10s 一次，带加分
    loop(() => {
      const u = pick(USERS)
      const g = pick(GIFTS)
      const qty = randInt(1, 3)
      const gain = g.price * qty
      // 加分 + 排序
      const target = scores.find((s) => s.id === u.id) || scores[0]
      target.score += gain
      scores.sort((a, b) => b.score - a.score)
      send(WS_TYPE.GIFT_DOWN, {
        userId: u.id, username: u.name,
        giftId: g.id, giftName: g.name, giftIcon: g.icon,
        quantity: qty, price: g.price, gain,
        timestamp: Date.now(),
      })
    }, 7000)

    // 在线人数：3 ~ 6s 抖动
    loop(() => {
      online = Math.max(120, online + randInt(-18, 28))
      send(WS_TYPE.ONLINE_COUNT, { count: online })
    }, 4000)

    // 库存缓慢消耗：4 ~ 8s 扣 1~3
    loop(() => {
      if (stock <= 0) return
      stock = Math.max(0, stock - randInt(1, 3))
      send('STOCK_UPDATE', { stock, total: stockTotal })
    }, 5500)
  }

  function handleOut(msg) {
    // 上行弹幕：回声给自己
    if (msg.type === WS_TYPE.BARRAGE) {
      send(WS_TYPE.BARRAGE_DOWN, {
        userId: 0,
        username: '我',
        avatar: '',
        content: msg.data.content,
        timestamp: Date.now(),
        self: true,
      })
      return
    }
    // 上行送礼：加分并广播
    if (msg.type === WS_TYPE.GIFT) {
      const g = GIFTS.find((x) => x.id === msg.data.giftId) || GIFTS[0]
      const qty = msg.data.quantity || 1
      const gain = g.price * qty
      send(WS_TYPE.GIFT_DOWN, {
        userId: 0, username: '我',
        giftId: g.id, giftName: g.name, giftIcon: g.icon,
        quantity: qty, price: g.price, gain,
        timestamp: Date.now(),
        self: true,
      })
      return
    }
    // 上行秒杀：随机延迟 + 概率扣库存，回送结果 + 库存同步
    if (msg.type === WS_TYPE.SEC_KILL) {
      const reqId = msg.messageId
      const latency = 800 + Math.random() * 600
      setTimeout(() => {
        // 库存为 0 直接失败
        if (stock <= 0) {
          send(WS_TYPE.SEC_KILL_RESULT, {
            ok: false, reason: 'soldout', message: '已售罄',
            reqId, timestamp: Date.now(),
          })
          return
        }
        // 60% 命中
        const ok = Math.random() < 0.6
        if (ok) {
          stock = Math.max(0, stock - 1)
          send('STOCK_UPDATE', { stock, total: stockTotal })
        }
        send(WS_TYPE.SEC_KILL_RESULT, {
          ok,
          reason: ok ? 'success' : 'slow',
          message: ok ? '抢购成功' : '手速慢了',
          reqId,
          timestamp: Date.now(),
        })
      }, latency)
      return
    }
    if (msg.type === WS_TYPE.PING) {
      send(WS_TYPE.PONG, {})
    }
  }

  function stop() {
    timers.forEach(clearInterval)
    timers = []
  }

  return { start, stop, handleOut,
    // 供页面初始化读取的快照
    snapshot: () => ({ online, scores: [...scores], stock, stockTotal }) }
}
