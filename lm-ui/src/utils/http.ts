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

// ----- 请求拦截器 -----
http.interceptors.request.use((config) => {
  const c = config as RetryableConfig & { headers: NonNullable<typeof config.headers> }
  c._start = Date.now()
  // 1. 设备指纹（所有请求）
  c.headers['X-Device-Id'] = getDeviceId()
  // 2. 幂等键（仅写操作）
  if (['post', 'put', 'delete'].includes((c.method || '').toLowerCase())) {
    c.headers['X-Idempotency-Key'] = crypto.randomUUID()
  }
  // 3. 认证 Token
  const token = localStorage.getItem('accessToken')
  if (token) c.headers.Authorization = `Bearer ${token}`
  return c
})

// ----- 响应拦截器（双 Token 自动刷新，联调文档 §2.2）-----
let isRefreshing = false
let pendingQueue: Array<() => void> = []

async function redirectToLogin(): Promise<void> {
  localStorage.removeItem('accessToken')
  localStorage.removeItem('refreshToken')
  // 动态引入避免循环依赖
  const { default: router } = await import('@/router')
  if (router.currentRoute.value.path !== '/login') {
    router.push({ path: '/login', query: { redirect: router.currentRoute.value.fullPath } })
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

    // 并发 401 合并：排队等待唯一的一次 refresh
    if (isRefreshing) {
      return new Promise((resolve) => {
        pendingQueue.push(() => resolve(http(config)))
      })
    }

    config._retry = true
    isRefreshing = true
    try {
      const { data } = await http.post<ApiResponse<LoginResponse>>('/auth/refresh', {
        refreshToken: localStorage.getItem('refreshToken'),
      })
      localStorage.setItem('accessToken', data.data.accessToken)
      localStorage.setItem('refreshToken', data.data.refreshToken)
      pendingQueue.forEach((cb) => cb())
      pendingQueue = []
      config.headers = { ...config.headers, Authorization: `Bearer ${data.data.accessToken}` }
      return http(config)
    } catch (e) {
      pendingQueue = []
      await redirectToLogin()
      return Promise.reject(e instanceof ApiError ? e : new ApiError(1013, '登录已过期，请重新登录'))
    } finally {
      isRefreshing = false
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
