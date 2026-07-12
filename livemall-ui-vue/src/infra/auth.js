// 双 Token 存取 + 并发刷新合并
// 与后端 2.2-用户服务与认证 对齐：
//   Access  (JWT, 15min) → sessionStorage（刷新页保活）
//   Refresh (7d, Redis)  → localStorage（无感刷新）

import { STORAGE_KEY } from '@/constants'

const sStorage = {
  get(k) {
    try { return sessionStorage.getItem(k) } catch { return null }
  },
  set(k, v) {
    try { sessionStorage.setItem(k, v) } catch { /* ignore */ }
  },
  del(k) {
    try { sessionStorage.removeItem(k) } catch { /* ignore */ }
  },
}

const lStorage = {
  get(k) {
    try { return localStorage.getItem(k) } catch { return null }
  },
  set(k, v) {
    try { localStorage.setItem(k, v) } catch { /* ignore */ }
  },
  del(k) {
    try { localStorage.removeItem(k) } catch { /* ignore */ }
  },
}

export const tokens = {
  getAccessToken() { return sStorage.get(STORAGE_KEY.ACCESS) || null },
  setAccessToken(v) { v ? sStorage.set(STORAGE_KEY.ACCESS, v) : sStorage.del(STORAGE_KEY.ACCESS) },

  getRefreshToken() { return lStorage.get(STORAGE_KEY.REFRESH) || null },
  setRefreshToken(v) { v ? lStorage.set(STORAGE_KEY.REFRESH, v) : lStorage.del(STORAGE_KEY.REFRESH) },

  clear() {
    sStorage.del(STORAGE_KEY.ACCESS)
    lStorage.del(STORAGE_KEY.REFRESH)
    lStorage.del(STORAGE_KEY.USER)
    // 注意：deviceId 不在 clear 范围内，它是设备级标识，登出后仍保留
    // 同一设备的用户登出再登入，应复用同一 deviceId（对应后端 device_sessions 语义）
  },
}

/**
 * 设备 ID 管理 · 对应后端 X-Device-Id Header（登录/注册必传，UUID 格式，客户端持久化）
 * 设计依据：md/docs/3-功能实现细节/3.1-用户服务/3.1.2-登录.md
 *   - 首次访问生成 UUID v4 并写入 localStorage
 *   - 后续读取直接复用，保证同一浏览器同一设备 ID 稳定
 *   - 登出不清除（设备级标识，跨用户会话保留）
 */
function genUuid() {
  if (crypto?.randomUUID) return crypto.randomUUID()
  // 兜底：手写 UUID v4
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0
    const v = c === 'x' ? r : (r & 0x3) | 0x8
    return v.toString(16)
  })
}

export const device = {
  /** 读取设备 ID，不存在则生成并持久化 */
  ensureId() {
    let id = lStorage.get(STORAGE_KEY.DEVICE)
    if (!id) {
      id = genUuid()
      lStorage.set(STORAGE_KEY.DEVICE, id)
    }
    return id
  },
  get() { return lStorage.get(STORAGE_KEY.DEVICE) || null },
}

/**
 * 并发刷新合并：同一时刻多个 401 触发 refresh 时，
 * 只发一个 /auth/refresh，其余复用同一 Promise。
 * 对应后端 2.2 §并发刷新防护。
 */
let _refreshing = null

export function getRefreshing() { return _refreshing }
export function setRefreshing(p) { _refreshing = p }
export function clearRefreshing() { _refreshing = null }
