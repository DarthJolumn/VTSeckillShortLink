// WebSocket 客户端 · 心跳 / 指数退避重连 / 消息分发总线 / 状态枚举
// 对齐后端 2.4-WebSocket长连接：
//   - 客户端 30s 发 PING，服务端 60s 无 PONG 判定超时
//   - 断线后服务端把消息存到 offline:{userId}，重连时自动补发
//   - 消息格式 { type, data, messageId, timestamp }
//
// 设计：与后端解耦的 transport 层。当 mock=true 时不建连，
// 由 ws-mock.js 模拟服务端推送，便于无后端演示。

import { WS_HEARTBEAT_MS, WS_PONG_TIMEOUT_MS, WS_RECONNECT_MAX, WS_TYPE } from '@/constants'
import { tokens } from './auth'

export const WS_STATUS = Object.freeze({
  IDLE: 'idle',
  CONNECTING: 'connecting',
  OPEN: 'open',
  RECONNECTING: 'reconnecting',
  CLOSED: 'closed',
})

const RECONNECT_BACKOFF = [1000, 2000, 4000, 8000, 16000, 30000]

export class WsClient {
  constructor({ roomId, mock = false, onStatus, onOpen, onClose }) {
    this.roomId = roomId
    this.mock = mock
    this.onStatus = onStatus || (() => {})
    this.onOpen = onOpen || (() => {})
    this.onClose = onClose || (() => {})

    this._ws = null
    this._status = WS_STATUS.IDLE
    this._listeners = new Map()      // type -> Set<fn>
    this._heartbeatTimer = null
    this._pongTimer = null
    this._reconnectCount = 0
    this._manualClose = false
    this._mockDriver = null
  }

  get status() { return this._status }

  // 暴露给上层读取 mock 快照（后端模式下返回 null）
  getSnapshot() {
    return this._mockDriver?.snapshot?.() || null
  }

  // —— 生命周期 ——
  connect() {
    if (this.mock) return this._startMock()
    this._setStatus(WS_STATUS.CONNECTING)
    // VITE_WS_BASE 形如 ws://host/ws 或 wss://host/ws；末尾不带 /ws 也兼容
    const base = (import.meta.env.VITE_WS_BASE || `ws://${location.host}/ws`).replace(/\/ws$/, '')
    const token = tokens.getAccessToken() || 'demo-token'
    const url = `${base}/ws/live/${this.roomId}?token=${encodeURIComponent(token)}`
    try {
      this._ws = new WebSocket(url)
    } catch (e) {
      this._scheduleReconnect()
      return
    }
    this._ws.onopen = this._onOpen.bind(this)
    this._ws.onmessage = this._onMessage.bind(this)
    this._ws.onerror = () => { /* 由 onclose 兜底重连 */ }
    this._ws.onclose = this._onCloseEvt.bind(this)
  }

  async _startMock() {
    this._setStatus(WS_STATUS.CONNECTING)
    const mod = await import('./ws-mock')
    this._mockDriver = mod.createMockDriver({ roomId: this.roomId, send: this._emit.bind(this) })
    this._setStatus(WS_STATUS.OPEN)
    this.onOpen()
    this._startHeartbeat()
    this._mockDriver.start()
  }

  _onOpen() {
    this._reconnectCount = 0
    this._setStatus(WS_STATUS.OPEN)
    this.onOpen()
    this._startHeartbeat()
  }

  _onMessage(evt) {
    let msg
    try { msg = JSON.parse(evt.data) } catch { return }
    if (msg.type === WS_TYPE.PONG) {
      this._onPong()
      return
    }
    this._emit(msg.type, msg.data, msg)
  }

  _onCloseEvt() {
    this._clearTimers()
    if (this._manualClose) {
      this._setStatus(WS_STATUS.CLOSED)
      this.onClose()
      return
    }
    this._scheduleReconnect()
  }

  _scheduleReconnect() {
    if (this._reconnectCount >= WS_RECONNECT_MAX) {
      this._setStatus(WS_STATUS.CLOSED)
      this.onClose()
      return
    }
    this._setStatus(WS_STATUS.RECONNECTING)
    const delay = RECONNECT_BACKOFF[this._reconnectCount] || 30000
    this._reconnectCount++
    setTimeout(() => this.connect(), delay)
  }

  // —— 心跳 ——
  _startHeartbeat() {
    this._clearTimers()
    this._heartbeatTimer = setInterval(() => {
      this._sendRaw({ type: WS_TYPE.PING, data: {}, timestamp: Date.now() })
      // 清掉上一轮残留的 PONG 超时定时器，避免心跳堆积导致误重连
      if (this._pongTimer) { clearTimeout(this._pongTimer); this._pongTimer = null }
      // 60s 内没收到 PONG 判定超时，主动重连
      this._pongTimer = setTimeout(() => {
        try { this._ws?.close() } catch { /* ignore */ }
      }, WS_PONG_TIMEOUT_MS)
    }, WS_HEARTBEAT_MS)
  }

  _onPong() {
    if (this._pongTimer) { clearTimeout(this._pongTimer); this._pongTimer = null }
  }

  _clearTimers() {
    if (this._heartbeatTimer) { clearInterval(this._heartbeatTimer); this._heartbeatTimer = null }
    if (this._pongTimer) { clearTimeout(this._pongTimer); this._pongTimer = null }
  }

  // —— 发送 ——
  send(type, data = {}) {
    const msg = {
      type,
      data,
      messageId: crypto?.randomUUID?.() || `${Date.now()}-${Math.random()}`,
      timestamp: Date.now(),
    }
    if (this.mock) {
      // mock 模式把上行消息交给 mock driver 处理（如发弹幕/送礼）
      this._mockDriver?.handleOut?.(msg)
      return
    }
    this._sendRaw(msg)
  }

  _sendRaw(msg) {
    if (this._ws && this._ws.readyState === WebSocket.OPEN) {
      this._ws.send(JSON.stringify(msg))
    }
  }

  // —— 分发 ——
  on(type, fn) {
    if (!this._listeners.has(type)) this._listeners.set(type, new Set())
    this._listeners.get(type).add(fn)
    return () => this.off(type, fn)
  }

  off(type, fn) {
    this._listeners.get(type)?.delete(fn)
  }

  _emit(type, data, raw) {
    const set = this._listeners.get(type)
    if (set) for (const fn of set) { try { fn(data, raw) } catch { /* ignore */ } }
    // 通配
    const all = this._listeners.get('*')
    if (all) for (const fn of all) { try { fn(type, data, raw) } catch { /* ignore */ } }
  }

  // —— 销毁 ——
  destroy() {
    this._manualClose = true
    this._clearTimers()
    this._mockDriver?.stop?.()
    this._mockDriver = null
    try { this._ws?.close() } catch { /* ignore */ }
    this._ws = null
    this._listeners.clear()
    this._setStatus(WS_STATUS.CLOSED)
  }

  _setStatus(s) {
    this._status = s
    this.onStatus(s)
  }
}
