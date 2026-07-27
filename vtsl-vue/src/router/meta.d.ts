import 'vue-router'

declare module 'vue-router' {
  interface RouteMeta {
    /** 需要登录 */
    requiresAuth?: boolean
    /** 仅游客可访问（登录/注册页） */
    guestOnly?: boolean
    /** 角色限制（AUDIENCE/ANCHOR/ADMIN） */
    roles?: string[]
    /** 页面标题 */
    title?: string
  }
}
