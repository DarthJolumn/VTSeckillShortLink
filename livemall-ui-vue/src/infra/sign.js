// HMAC-SHA256 签名生成
// 算法与后端 2.6/3.6.3 SignVerifyGlobalFilter 对齐：
//   X-Timestamp = Date.now()
//   X-Nonce     = uuid
//   X-Sign      = HMAC-SHA256(timestamp + nonce, secret)
//   X-AppKey    = AppKey（用于服务端查 Secret）
//
// ⚠️ 安全困境见 前端设计方案.md §10.4：浏览器是公开客户端，
//    Secret 内置会暴露。仅用于面试/演示，生产须走 BFF 代签或网关豁免。

import HmacSHA256 from 'crypto-js/hmac-sha256'
import { STORAGE_KEY } from '@/constants'

const APP_KEY = import.meta.env.VITE_APP_KEY || 'livemall'
const APP_SECRET = import.meta.env.VITE_APP_SECRET || 'livemall2026'

function uuid() {
  if (crypto?.randomUUID) return crypto.randomUUID()
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0
    const v = c === 'x' ? r : (r & 0x3) | 0x8
    return v.toString(16)
  })
}

/**
 * 生成签名头。登录/注册接口需要。
 * Nonce 防重放由后端 Redis SETNX 60s 兜底，前端只负责生成。
 */
export function genSignHeaders() {
  const timestamp = String(Date.now())
  const nonce = uuid()
  const raw = timestamp + nonce
  const sign = HmacSHA256(raw, APP_SECRET).toString()
  return {
    'X-Timestamp': timestamp,
    'X-Nonce': nonce,
    'X-Sign': sign,
    'X-AppKey': APP_KEY,
  }
}

// 便于其他模块读取当前 AppKey（如 WS 鉴权扩展）
export const appKey = APP_KEY
