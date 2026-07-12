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
  },
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
