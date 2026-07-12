// 路由守卫 · 鉴权 / 角色 / 标题
import { useUserStore } from '@/stores/user'
import { ROLE } from '@/constants'

// 各角色默认落地页
const HOME_BY_ROLE = {
  [ROLE.AUDIENCE]: '/',
  [ROLE.ANCHOR]: '/anchor/stream',
  [ROLE.ADMIN]: '/admin/dashboard',
}

export function installGuards(router) {
  router.beforeEach((to, from, next) => {
    const userStore = useUserStore()
    document.title = to.meta.title ? `${to.meta.title} · LiveMall` : 'LiveMall · 直播秒杀'

    // 公共页直接放行
    if (to.meta.public) {
      // 已登录用户访问登录/注册 → 跳首页
      if (userStore.isLogin && (to.name === 'login' || to.name === 'register')) {
        return next({ path: HOME_BY_ROLE[userStore.role] || '/' })
      }
      return next()
    }

    // 需登录
    if (!userStore.isLogin) {
      return next({ path: '/login', query: { redirect: to.fullPath } })
    }

    // 角色校验
    const roles = to.meta.roles
    if (roles && !roles.includes(userStore.role)) {
      return next('/403')
    }

    next()
  })
}
