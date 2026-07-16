// 路由守卫 · 鉴权 / 角色 / 标题
// v1 匿名观看改造：首页 + 直播间匿名可看，需登录页面弹窗不跳转
import { useUserStore } from '@/stores/user'
import { useLoginModal } from '@/composables/useLoginModal'
import { ROLE } from '@/constants'

const HOME_BY_ROLE = {
  [ROLE.AUDIENCE]: '/',
  [ROLE.ANCHOR]: '/anchor/stream',
  [ROLE.ADMIN]: '/admin/dashboard',
}

export function installGuards(router) {
  router.beforeEach((to, from, next) => {
    const userStore = useUserStore()
    document.title = to.meta.title ? `${to.meta.title} · LiveMall` : 'LiveMall · 直播秒杀'

    // 1. 公共页直接放行
    if (to.meta.public) {
      if (userStore.isLogin && (to.name === 'login' || to.name === 'register')) {
        return next({ path: HOME_BY_ROLE[userStore.role] || '/' })
      }
      return next()
    }

    // 2. 首页 + 直播间 — 匿名可看
    if (to.name === 'home' || to.name === 'live-room') {
      return next()
    }

    // 3. 需登录页面 — 未登录弹出登录弹窗，不跳转
    if (!userStore.isLogin) {
      const { showLoginModal } = useLoginModal()
      showLoginModal({ redirect: to.fullPath })
      return next(false)
    }

    // 4. 角色校验
    const roles = to.meta.roles
    if (roles && !roles.includes(userStore.role)) {
      return next('/403')
    }

    next()
  })
}