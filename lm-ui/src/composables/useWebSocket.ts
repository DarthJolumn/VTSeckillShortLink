import { ref, onUnmounted, isRef, type Ref } from 'vue'
import { getDeviceId } from '@/utils/device'
import { useSeckillStore } from '@/stores/seckill'
import { getGiftDef } from '@/constants/gifts'
import type { WsServerMessage, WsClientMessage, WsGift, ChatItem } from '@/types/ws'

/** 客户端 PING 间隔 30s（服务端 60s 无消息强制关闭） */
const PING_INTERVAL = 30_000
const MAX_RETRY = 5
/** 弹幕列表上限，超出裁头 */
const MSG_LIMIT = 100

type MessageHandler = (msg: WsServerMessage) => void
type KickHandler = () => void
type GiftHandler = (gift: WsGift['data']) => void

export function useWebSocket(roomId: Ref<number> | number) {
  const ws = ref<WebSocket | null>(null)
  const connected = ref(false)
  const authenticated = ref(false)
  const displayName = ref('')
  const online = ref(0)
  const msgList = ref<ChatItem[]>([])

  let pingTimer: ReturnType<typeof setInterval> | undefined
  let reconnectTimer: ReturnType<typeof setTimeout> | undefined
  let retryCount = 0
  let manualClose = false

  // —— 事件订阅（组件可挂自己的处理器）——
  const messageHandlers = new Set<MessageHandler>()
  const kickHandlers = new Set<KickHandler>()
  const giftHandlers = new Set<GiftHandler>()

  function onMessage(h: MessageHandler) { messageHandlers.add(h); return () => { messageHandlers.delete(h) } }
  function onKick(h: KickHandler) { kickHandlers.add(h); return () => { kickHandlers.delete(h) } }
  function onGift(h: GiftHandler) { giftHandlers.add(h); return () => { giftHandlers.delete(h) } }

  function connect(token?: string) {
    manualClose = false
    const rid = isRef(roomId) ? roomId.value : roomId
    if (!rid) return
    const params = new URLSearchParams({ deviceId: getDeviceId() })
    if (token) params.set('token', token)
    const proto = location.protocol === 'https:' ? 'wss' : 'ws'
    ws.value = new WebSocket(`${proto}://${location.host}/ws/live/${rid}?${params}`)

    ws.value.onopen = () => {
      connected.value = true
      retryCount = 0
      startPing()
    }

    ws.value.onmessage = (e: MessageEvent) => {
      try {
        routeMessage(JSON.parse(e.data) as WsServerMessage)
      } catch { /* 非 JSON 消息忽略 */ }
    }

    ws.value.onclose = () => {
      connected.value = false
      authenticated.value = false
      stopPing()
      scheduleReconnect()
    }

    ws.value.onerror = () => { /* onclose 会随后触发，统一在那里处理 */ }
  }

  function routeMessage(msg: WsServerMessage) {
    switch (msg.type) {
      case 'CONNECTED':
        displayName.value = msg.data.displayName
        online.value = msg.data.online
        authenticated.value = !msg.data.anonymous
        break
      case 'AUTH_OK':
        authenticated.value = true
        displayName.value = msg.data.displayName
        break
      case 'PONG':
        break
      case 'BARRAGE':
        pushMsg({ kind: 'barrage', ...msg.data })
        break
      case 'GIFT': {
        // 后端只转发 giftId/quantity，元数据本地补齐
        const def = getGiftDef(msg.data.giftId)
        const enriched: WsGift['data'] = {
          ...msg.data,
          giftName: def.name,
          giftIcon: def.icon,
          price: def.price,
          gain: def.price * (msg.data.quantity || 1),
        }
        pushMsg({ kind: 'gift', ...enriched })
        giftHandlers.forEach(h => h(enriched))
        break
      }
      case 'SEC_KILL_RESULT':
        useSeckillStore().resolveOrderCallback(msg.data)
        break
      case 'KICK':
        disconnect()
        kickHandlers.forEach(h => h())
        break
      default:
        break
    }
    messageHandlers.forEach(h => h(msg))
  }

  function pushMsg(item: ChatItem) {
    msgList.value.push(item)
    if (msgList.value.length > MSG_LIMIT) {
      msgList.value.splice(0, msgList.value.length - MSG_LIMIT)
    }
  }

  function send(msg: WsClientMessage) {
    if (ws.value?.readyState === WebSocket.OPEN) {
      ws.value.send(JSON.stringify(msg))
    }
  }

  /** 匿名连接运行时升级为认证连接 */
  function sendAuth(token: string) { send({ type: 'AUTH', data: { token } }) }
  function sendBarrage(content: string) { send({ type: 'BARRAGE', data: { content } }) }
  function sendGift(giftId: number, quantity = 1) { send({ type: 'GIFT', data: { giftId, quantity } }) }

  function startPing() {
    stopPing()
    pingTimer = setInterval(() => send({ type: 'PING' }), PING_INTERVAL)
  }
  function stopPing() {
    if (pingTimer) { clearInterval(pingTimer); pingTimer = undefined }
  }

  /** 指数退避重连：1s/2s/4s/8s/10s，最多 5 次 */
  function scheduleReconnect() {
    if (manualClose || retryCount >= MAX_RETRY) return
    const delay = Math.min(1000 * 2 ** retryCount, 10_000)
    retryCount++
    reconnectTimer = setTimeout(() => connect(), delay)
  }

  function disconnect() {
    manualClose = true
    stopPing()
    if (reconnectTimer) { clearTimeout(reconnectTimer); reconnectTimer = undefined }
    ws.value?.close()
    ws.value = null
    connected.value = false
  }

  onUnmounted(disconnect)

  return { ws, connected, authenticated, displayName, online, msgList,
           connect, disconnect, sendAuth, sendBarrage, sendGift,
           onMessage, onKick, onGift }
}
