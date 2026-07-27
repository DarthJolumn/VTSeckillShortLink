import router from './index'
import { useAuthStore } from '@/stores/auth'
import { useUserStore } from '@/stores/user'

/** 角色码 → 角色名（后端 RoleEnum: 1=观众 2=主播 3=管理员） */
const ROLE_MAP: Record<number, string> = { 1: 'AUDIENCE', 2: 'ANCHOR', 3: 'ADMIN' }

router.beforeEach(async (to, _from, next) => {
  const auth = useAuthStore()

  // C. 已登录用户禁止访问登录/注册页
  if (to.meta.guestOnly && auth.isLoggedIn) {
    return next('/')
  }

  // A. 登录校验
  if (to.meta.requiresAuth && !auth.isLoggedIn) {
    return next({ path: '/login', query: { redirect: to.fullPath } })
  }

  // B. 角色权限（提前用 localStorage 缓存的角色检查，不阻塞导航）
  if (to.meta.roles) {
    const cachedRole = ROLE_MAP[auth.role] || 'AUDIENCE'
    if (!to.meta.roles.includes(cachedRole)) {
      return next('/403')
    }
  }

  // 已登录但资料未加载（刷新页面）→ 后台拉取完整资料
  if (auth.isLoggedIn && !auth.user && (to.meta.requiresAuth || to.meta.roles)) {
    try {
      await useUserStore().fetchProfile()
    } catch (e) {
      const err = e as { code?: number }
      if (err?.code === 401 || err?.code === 1013) {
        auth.clearTokens()
        return next({ path: '/login', query: { redirect: to.fullPath } })
      }
      // 非鉴权错误（网络/服务器异常）→ 已通过的导航放行，未通过的阻止
      if (to.meta.requiresAuth || to.meta.roles) {
        return next(false)
      }
    }
  }

  next()
})
