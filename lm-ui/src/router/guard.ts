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

  // 已登录但资料未加载（刷新页面）→ 先拉资料，角色判断依赖它
  if (auth.isLoggedIn && !auth.user && (to.meta.requiresAuth || to.meta.roles)) {
    try {
      await useUserStore().fetchProfile()
    } catch {
      // 401 已由拦截器处理跳转
      return next(false)
    }
  }

  // B. 角色权限
  if (to.meta.roles) {
    const userRole = ROLE_MAP[auth.role] || 'AUDIENCE'
    if (!to.meta.roles.includes(userRole)) {
      return next('/403')
    }
  }

  next()
})
