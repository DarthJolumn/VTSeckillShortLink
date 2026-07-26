import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  // ── 无需鉴权 ──
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/LoginPage.vue'),
    meta: { guestOnly: true, title: '登录' },
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/login/RegisterPage.vue'),
    meta: { guestOnly: true, title: '注册' },
  },
  {
    path: '/',
    name: 'LiveHall',
    component: () => import('@/views/c/LiveHallPage.vue'),
    meta: { title: '直播大厅' },
  },
  {
    path: '/live/:roomId',
    name: 'LiveRoom',
    component: () => import('@/views/c/LiveRoomPage.vue'),
    meta: { title: '直播间' }, // 匿名可进（WS 支持匿名连接）
  },

  // ── 用户中心（需登录）──
  {
    path: '/user',
    component: () => import('@/layouts/UserLayout.vue'),
    meta: { requiresAuth: true },
    children: [
      { path: 'profile', name: 'UserProfile', component: () => import('@/views/c/ProfilePage.vue'), meta: { title: '个人中心' } },
      { path: 'orders', name: 'UserOrders', component: () => import('@/views/c/OrderListPage.vue'), meta: { title: '我的订单' } },
      { path: 'devices', name: 'UserDevices', component: () => import('@/views/c/DeviceManagerPage.vue'), meta: { title: '设备管理' } },
    ],
  },

  // ── 主播中心（ANCHOR / ADMIN）──
  {
    path: '/streamer',
    component: () => import('@/layouts/StreamerLayout.vue'),
    meta: { requiresAuth: true, roles: ['ANCHOR', 'ADMIN'] },
    children: [
      { path: 'panel', name: 'StudioPage', component: () => import('@/views/b/StudioPage.vue'), meta: { title: '直播工作台' } },
      { path: 'seckill', name: 'SeckillAdmin', component: () => import('@/views/b/SeckillAdminPage.vue'), meta: { title: '秒杀管理' } },
      { path: 'shortlink', name: 'ShortLinkAdmin', component: () => import('@/views/b/ShortLinkAdminPage.vue'), meta: { title: '短链管理' } },
    ],
  },

  // ── 异常页 ──
  { path: '/403', name: 'Forbidden', component: () => import('@/views/Error/403.vue'), meta: { title: '无权限' } },
  { path: '/:pathMatch(.*)*', name: 'NotFound', component: () => import('@/views/Error/404.vue'), meta: { title: '页面不存在' } },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.afterEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} · LiveMall` : 'LiveMall 直播商城'
})

export default router
