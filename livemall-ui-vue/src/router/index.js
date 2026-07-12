// 路由表 · 角色分入口
import { createRouter, createWebHistory } from 'vue-router'
import { ROLE } from '@/constants'

const routes = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/auth/Login.vue'),
    meta: { public: true, title: '登录' },
  },
  {
    path: '/register',
    name: 'register',
    component: () => import('@/views/auth/Register.vue'),
    meta: { public: true, title: '注册' },
  },
  {
    path: '/',
    component: () => import('@/layouts/AudienceLayout.vue'),
    children: [
      { path: '', name: 'home', component: () => import('@/views/audience/Home.vue'), meta: { title: '直播间广场' } },
      { path: 'live/:roomId', name: 'live-room', component: () => import('@/views/audience/LiveRoom.vue'), meta: { title: '直播间' } },
      { path: 'profile', name: 'profile', component: () => import('@/views/audience/Profile.vue'), meta: { title: '个人中心' } },
      { path: 'devices', name: 'devices', component: () => import('@/views/audience/Devices.vue'), meta: { title: '设备管理' } },
      { path: 'orders', name: 'orders', component: () => import('@/views/audience/Orders.vue'), meta: { title: '我的订单' } },
      { path: 'orders/:orderNo', name: 'order-detail', component: () => import('@/views/audience/OrderDetail.vue'), meta: { title: '订单详情' } },
    ],
  },
  {
    path: '/anchor',
    component: () => import('@/layouts/AnchorLayout.vue'),
    meta: { roles: [ROLE.ANCHOR, ROLE.ADMIN] },
    children: [
      { path: 'stream', name: 'anchor-stream', component: () => import('@/views/anchor/StreamConsole.vue'), meta: { title: '开播控制台' } },
      { path: 'activity', name: 'anchor-activity', component: () => import('@/views/anchor/ActivityManage.vue'), meta: { title: '秒杀活动管理' } },
      { path: 'data', name: 'anchor-data', component: () => import('@/views/anchor/LiveData.vue'), meta: { title: '直播数据' } },
    ],
  },
  {
    path: '/admin',
    component: () => import('@/layouts/AdminLayout.vue'),
    meta: { roles: [ROLE.ADMIN] },
    children: [
      { path: 'users', name: 'admin-users', component: () => import('@/views/admin/UserManage.vue'), meta: { title: '用户管理' } },
      { path: 'activities', name: 'admin-activities', component: () => import('@/views/admin/ActivityOverview.vue'), meta: { title: '活动总览' } },
      { path: 'dashboard', name: 'admin-dashboard', component: () => import('@/views/admin/Dashboard.vue'), meta: { title: '实时数据大屏' } },
    ],
  },
  { path: '/403', name: 'forbidden', component: () => import('@/views/Forbidden.vue'), meta: { public: true, title: '无权限' } },
  { path: '/:pathMatch(.*)*', name: 'not-found', component: () => import('@/views/NotFound.vue'), meta: { public: true, title: '页面走丢了' } },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior() { return { top: 0 } },
})

export default router
