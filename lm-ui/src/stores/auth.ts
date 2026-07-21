import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { LoginResponse } from '@/types/api'
import type { UserProfile } from '@/types/user'

export const useAuthStore = defineStore('auth', () => {
  const accessToken = ref(localStorage.getItem('accessToken') || '')
  const refreshToken = ref(localStorage.getItem('refreshToken') || '')
  const user = ref<UserProfile | null>(null)

  const isLoggedIn = computed(() => !!accessToken.value)
  const role = computed(() => user.value?.role ?? 0)
  const isAnchor = computed(() => role.value >= 2) // 2=ANCHOR 3=ADMIN

  function setTokens(res: LoginResponse) {
    accessToken.value = res.accessToken
    refreshToken.value = res.refreshToken
    localStorage.setItem('accessToken', res.accessToken)
    localStorage.setItem('refreshToken', res.refreshToken)
  }

  function setUser(u: UserProfile) {
    user.value = u
  }

  function clearTokens() {
    accessToken.value = ''
    refreshToken.value = ''
    user.value = null
    localStorage.removeItem('accessToken')
    localStorage.removeItem('refreshToken')
  }

  return { accessToken, refreshToken, user, isLoggedIn, role, isAnchor, setTokens, setUser, clearTokens }
})
