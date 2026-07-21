import { post } from '@/utils/http'
import { useAuthStore } from '@/stores/auth'
import { useUserStore } from '@/stores/user'
import type { LoginResponse } from '@/types/api'
import type { LoginRequest, RegisterRequest } from '@/types/auth'

export function useAuth() {
  const authStore = useAuthStore()
  const userStore = useUserStore()

  /** 登录（X-Device-Id 由拦截器自动注入） */
  async function login(req: LoginRequest) {
    const res = await post<LoginResponse>('/auth/login', req)
    authStore.setTokens(res.data)
    await userStore.fetchProfile()
    return res.data
  }

  async function register(req: RegisterRequest) {
    await post('/auth/register', req)
  }

  async function logout() {
    try {
      await post('/auth/logout', { refreshToken: authStore.refreshToken })
    } finally {
      authStore.clearTokens()
    }
  }

  async function refresh() {
    const res = await post<LoginResponse>('/auth/refresh', {
      refreshToken: authStore.refreshToken,
    })
    authStore.setTokens(res.data)
  }

  return { login, register, logout, refresh }
}
