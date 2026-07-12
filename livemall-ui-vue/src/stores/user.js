// 用户状态 · 登录/注册/刷新/资料/角色
import { defineStore } from 'pinia'
import { authApi, userApi } from '@/api/user'
import { tokens } from '@/infra/auth'
import { ROLE } from '@/constants'

export const useUserStore = defineStore('user', {
  state: () => ({
    accessToken: null,
    refreshToken: null,
    userInfo: null, // { id, username, avatar, role, status, phone }
    loading: false,
  }),

  getters: {
    isLogin: (s) => !!s.accessToken,
    role: (s) => s.userInfo?.role ?? null,
    isAudience: (s) => s.userInfo?.role === ROLE.AUDIENCE,
    isAnchor: (s) => s.userInfo?.role === ROLE.ANCHOR,
    isAdmin: (s) => s.userInfo?.role === ROLE.ADMIN,
    nickname: (s) => s.userInfo?.nickname || s.userInfo?.username || '',
    avatar: (s) => s.userInfo?.avatar || '',
  },

  actions: {
    // 启动恢复
    bootstrap(access, refresh) {
      this.accessToken = access
      this.refreshToken = refresh
      const cached = localStorage.getItem('lm_user')
      if (cached) {
        try { this.userInfo = JSON.parse(cached) } catch { /* ignore */ }
      }
    },

    async login({ username, password }) {
      this.loading = true
      try {
        const data = await authApi.login({ username, password })
        // data = { accessToken, refreshToken, expiresIn }
        this.accessToken = data.accessToken
        this.refreshToken = data.refreshToken
        tokens.setAccessToken(data.accessToken)
        tokens.setRefreshToken(data.refreshToken)
        // 拉资料
        await this.fetchProfile()
        return true
      } finally {
        this.loading = false
      }
    },

    async register({ username, password, phone }) {
      await authApi.register({ username, password, phone })
      return true
    },

    async fetchProfile() {
      const profile = await userApi.profile()
      this.userInfo = profile
      localStorage.setItem('lm_user', JSON.stringify(profile))
      return profile
    },

    async updateProfile(data) {
      await userApi.updateProfile(data)
      await this.fetchProfile()
    },

    async logout() {
      try {
        if (this.refreshToken) await authApi.logout(this.refreshToken)
      } catch { /* ignore */ }
      this.reset()
    },

    reset() {
      this.accessToken = null
      this.refreshToken = null
      this.userInfo = null
      tokens.clear()
    },
  },
})
