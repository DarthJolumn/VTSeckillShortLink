import axios, { AxiosError, type AxiosRequestConfig, type AxiosResponse } from 'axios'
import type { ApiResponse, LoginResponse } from '@/types/api'
import { getDeviceId } from './device'
import { syncTimeFromHeaders } from './time'

/** 业务错误（后端 Result.code != 200 或 HTTP 错误） */
export class ApiError extends Error {
  code: number
  constructor(code: number, message: string) {
    super(message)
    this.code = code
  }
}

const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE || '/api',
  timeout: 15000,
})

// 标记重试过的请求，防死循环
interface RetryableConfig extends AxiosRequestConfig {
  _retry?: boolean
  _start?: number
}

/** 解析 JWT payload，返回 JSON 对象 */
function parseJwt(token: string): Record<string, unknown> | null {
  try {
    return JSON.parse(atob(token.split('.')[1]))
  } catch {
    return null
  }
}

let preRefreshing: Promise<void> | null = null

/** 在请求拦截器中预刷新：AT 剩不到 5 分钟时异步续期 */
function preRefreshIfNeeded(): void {
  if (preRefreshing) return
  const at = localStorage.getItem('accessToken')
  if (!at) return
  const payload = parseJwt(at)
  if (!payload || typeof payload.exp !== 'number') return
  const remaining = payload.exp * 1000 - Date.now()
  if (remaining < 5 * 60 * 1000) {
    preRefreshing = refreshTokenOnce().catch(() => {}).finally(() => { preRefreshing = null })
  }
}

// ----- 请求拦截器 -----
http.interceptors.request.use(async (config) => {
  const c = config as RetryableConfig & { headers: NonNullable<typeof config.headers> }
  c._start = Date.now()
  c.headers['X-Device-Id'] = getDeviceId()
  if (['post', 'put', 'delete'].includes((c.method || '').toLowerCase())) {
    c.headers['X-Idempotency-Key'] = crypto.randomUUID()
  }
  const isPublicAuth = c.url && /\/auth\/(login|register|refresh)$/.test(c.url)
  if (!isPublicAuth) {
    const at = localStorage.getItem('accessToken')
    if (at) {
      const payload = parseJwt(at)
      if (payload && typeof payload.exp === 'number') {
        const remaining = payload.exp * 1000 - Date.now()
        if (remaining <= 0) {
          // AT 已过期 → 阻塞刷新，失败则不带新 AT 由响应拦截器兜底 401
          await refreshTokenOnce().catch(() => {})
        } else {
          preRefreshIfNeeded()
        }
      }
    }
    const token = localStorage.getItem('accessToken')
    if (token) c.headers.Authorization = `Bearer ${token}`
  }
  const authHeader = typeof c.headers.Authorization === 'string' ? c.headers.Authorization : ''
  console.log(`[http] ${(c.method || '?').toUpperCase()} ${c.url} token=${authHeader.slice(0, 20)}...`)
  return c
})

// ----- 双 Token 自动刷新（联调文档 §2.2）-----
let isRefreshing = false
let pendingQueue: Array<() => void> = []

async function redirectToLogin(): Promise<void> {
  const { useAuthStore } = await import('@/stores/auth')
  useAuthStore().clearTokens()
  const { default: router } = await import('@/router')
  if (router.currentRoute.value.path !== '/login') {
    router.push({ path: '/login', query: { redirect: router.currentRoute.value.fullPath } })
  }
}

async function refreshTokenOnce(): Promise<void> {
  if (isRefreshing) {
    return new Promise((resolve) => pendingQueue.push(resolve))
  }
  isRefreshing = true
  const rt = localStorage.getItem('refreshToken')
  if (!rt) {
    isRefreshing = false
    throw new ApiError(1013, '登录已过期，请重新登录')
  }
  try {
    const { data } = await http.post<ApiResponse<LoginResponse>>('/auth/refresh', {
      refreshToken: rt,
    })
    localStorage.setItem('accessToken', data.data.accessToken)
    localStorage.setItem('refreshToken', data.data.refreshToken)
  } catch (e) {
    throw e instanceof ApiError ? e : new ApiError(1013, '登录已过期，请重新登录')
  } finally {
    const queue = pendingQueue.slice()
    pendingQueue = []
    queue.forEach((cb) => { try { cb() } catch {} })
    isRefreshing = false
  }
}

http.interceptors.response.use(
  // 解包为 ApiResponse（返回值类型与 axios 声明不符，属有意为之）
  (res: AxiosResponse<ApiResponse>): any => {
    // 服务端时间校准（用 RTT 中点估算）
    const start = (res.config as RetryableConfig)._start
    if (start) syncTimeFromHeaders(res.headers['date'] as string | null, start)
    const body = res.data
    // HTTP 200 但业务码非 200 → 抛业务错误
    if (body && typeof body.code === 'number' && body.code !== 200) {
      return Promise.reject(new ApiError(body.code, body.message || '请求失败'))
    }
    return body
  },
  async (error: AxiosError<ApiResponse>) => {
    const config = (error.config || {}) as RetryableConfig
    const { response } = error

    // 非 401 或已重试 → 归一化后拒绝
    if (!response || response.status !== 401 || config._retry) {
      const body = response?.data
      return Promise.reject(new ApiError(body?.code ?? response?.status ?? -1, body?.message || error.message || '网络异常'))
    }

    // 刷新接口本身 401 → 跳登录（防死循环）
    if (config.url === '/auth/refresh') {
      await redirectToLogin()
      return Promise.reject(new ApiError(1013, '登录已过期，请重新登录'))
    }

    config._retry = true
    try {
      await refreshTokenOnce()
      const newAt = localStorage.getItem('accessToken')
      if (newAt) config.headers = { ...config.headers, Authorization: `Bearer ${newAt}` }
      return http(config)
    } catch (e) {
      await redirectToLogin()
      return Promise.reject(e instanceof ApiError ? e : new ApiError(1013, '登录已过期，请重新登录'))
    }
  },
)

// ----- 业务封装：响应拦截器已解包为 ApiResponse -----
export function get<T>(url: string, config?: AxiosRequestConfig): Promise<ApiResponse<T>> {
  return http.get(url, config) as unknown as Promise<ApiResponse<T>>
}
export function post<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<ApiResponse<T>> {
  return http.post(url, data, config) as unknown as Promise<ApiResponse<T>>
}
export function put<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<ApiResponse<T>> {
  return http.put(url, data, config) as unknown as Promise<ApiResponse<T>>
}
export function del<T>(url: string, config?: AxiosRequestConfig): Promise<ApiResponse<T>> {
  return http.delete(url, config) as unknown as Promise<ApiResponse<T>>
}

export default http
