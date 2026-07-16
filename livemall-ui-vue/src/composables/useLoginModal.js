// 全局登录弹窗状态管理 · Promise 化回调
// 与 v1 匿名观看设计对齐：未登录操作触发弹窗，登录成功后继续原操作

import { ref } from 'vue'
import { useUserStore } from '@/stores/user'
import { ROLE } from '@/constants'

const visible = ref(false)
const activeTab = ref('login')
let resolvePromise = null
let redirectTarget = null

export function useLoginModal() {

  function showLoginModal(options = {}) {
    activeTab.value = options.tab || 'login'
    redirectTarget = options.redirect || null
    visible.value = true
    return new Promise((resolve) => {
      resolvePromise = resolve
    })
  }

  async function handleLogin(credentials) {
    const userStore = useUserStore()
    await userStore.login(credentials)
    _resolveSuccess(userStore.userInfo)
  }

  async function handleRegister(form) {
    const userStore = useUserStore()
    await userStore.register(form)
    // 注册成功后自动登录
    await userStore.login({ username: form.username, password: form.password })
    _resolveSuccess(userStore.userInfo)
  }

  function _resolveSuccess(userInfo) {
    visible.value = false
    resolvePromise?.(userInfo)
    resolvePromise = null
    if (redirectTarget && typeof redirectTarget === 'string') {
      window.location.href = redirectTarget
    }
    redirectTarget = null
  }

  function close() {
    visible.value = false
    resolvePromise?.(null)
    resolvePromise = null
    redirectTarget = null
  }

  return {
    visible,
    activeTab,
    showLoginModal,
    handleLogin,
    handleRegister,
    close,
  }
}