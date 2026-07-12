// axios 实例 · 统一返回 Result<T> 处理 / 签名注入 / 401 并发刷新合并 / 429 限流
// 对齐后端：2.6 网关 / 3.6.3 签名验签 / 2.2 双 Token

import axios from 'axios'
import { resolveError } from './error-code'
import { genSignHeaders } from './sign'
import {
  tokens, device, getRefreshing, setRefreshing, clearRefreshing,
} from './auth'
import { authApi } from '@/api/user'

// 全局轻量 toast（避免引入 UI 库；后续可替换为 NaiveUI useMessage）
let _toast = (msg, type = 'info') => {
  import('@/utils/toast').then((m) => m.showToast(msg, type))
}

const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE || '/api',
  timeout: 10000,
  headers: { 'Content-Type': 'application/json' },
})

// —— 请求拦截器 ——
http.interceptors.request.use((config) => {
  // 注入设备 ID（登录/注册必传，其余接口也统一带上，对应后端 X-Device-Id Header）
  if (!config.headers['X-Device-Id']) {
    config.headers['X-Device-Id'] = device.ensureId()
  }
  // 注入 JWT（登录/注册是公开接口，不携带旧 token，否则网关验 JWT 失败会 403）
  const isPublicAuth = /\/auth\/(login|register)$/.test(config.url)
  if (!isPublicAuth) {
    const access = tokens.getAccessToken()
    if (access && !config.headers.Authorization) {
      config.headers.Authorization = `Bearer ${access}`
    }
    // dev 模式绕过 Gateway，需要自行从 JWT 解出 userId 注入 X-User-Id
    // 生产环境由 Gateway 解 JWT 后注入此 header
    if (access && !config.headers['X-User-Id']) {
      try {
        const payload = JSON.parse(atob(access.split('.')[1]))
        if (payload.sub) config.headers['X-User-Id'] = payload.sub
      } catch { /* ignore malformed token */ }
    }
  }
  // 签名接口注入 X-Sign 头
  if (config.sign) {
    Object.assign(config.headers, genSignHeaders())
  }
  return config
})

// —— 响应拦截器 ——
http.interceptors.response.use(
  (resp) => {
    const body = resp.data
    // 非 Result 结构（如文件流）直接放行
    if (body == null || typeof body !== 'object' || !('code' in body)) {
      return resp.config._raw ? body : body
    }
    // Result<T> { code, message, data, timestamp }
    const { code, message, data } = body
    if (code === 200) {
      return resp.config._raw ? body : data
    }
    // 业务错误
    const info = resolveError(code, message)
    _toast(info.msg, info.type)
    const err = new Error(info.msg)
    err.code = code
    err.business = true
    err.payload = body
    return Promise.reject(err)
  },
  (error) => {
    // —— 401：触发刷新并重发 ——
    const status = error.response?.status
    const config = error.config
    if (status === 401 && config && !config._retried) {
      config._retried = true
      return refreshTokenOnce().then(() => {
        // 重发前重置 Authorization
        const access = tokens.getAccessToken()
        if (access) config.headers.Authorization = `Bearer ${access}`
        return http(config)
      }).catch((e) => {
        clearAllAndRedirectLogin()
        return Promise.reject(e)
      })
    }

    // —— 429：限流 ——
    if (status === 429) {
      _toast('操作太频繁，请稍后再试', 'warning')
      const e = new Error('rate limited')
      e.code = 429
      e.rateLimit = true
      return Promise.reject(e)
    }

    // —— 5xx / 网络错误 ——
    const msg = error.response?.data?.message
      || (error.code === 'ECONNABORTED' ? '请求超时，请重试' : '网络异常，请检查连接')
    _toast(msg, 'error')
    return Promise.reject(error)
  }
)

/**
 * 并发刷新合并：同一时刻只发一个 /auth/refresh
 * 对应后端 2.2 §并发刷新防护（前端侧）
 */
function refreshTokenOnce() {
  if (getRefreshing()) return getRefreshing()
  const refreshToken = tokens.getRefreshToken()
  if (!refreshToken) {
    return Promise.reject(new Error('no refresh token'))
  }
  const p = authApi.refresh(refreshToken)
    .then((body) => {
      // authApi.refresh 走 _raw，body = Result{ code, message, data }
      // data 结构兼容：{ accessToken } 或 { accessToken, refreshToken, expiresIn }
      const data = body?.data || body || {}
      const access = data.accessToken
      if (!access) throw new Error('refresh returned no accessToken')
      tokens.setAccessToken(access)
      if (data.refreshToken) tokens.setRefreshToken(data.refreshToken)
      clearRefreshing()
    })
    .catch((e) => {
      clearRefreshing()
      throw e
    })
  setRefreshing(p)
  return p
}

function clearAllAndRedirectLogin() {
  tokens.clear()
  // 跳登录（避免在循环依赖时直接 import router）
  if (location.pathname !== '/login') {
    location.href = `/login?redirect=${encodeURIComponent(location.pathname + location.search)}`
  }
}

// 允许外部替换 toast 实现
export function setToastFn(fn) { _toast = fn }

export default http
