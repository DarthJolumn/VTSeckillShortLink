import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import { installGuards } from './router/guards'
import { useUserStore } from './stores/user'
import { orderStore } from './stores/order'
import { tokens } from './infra/auth'
import { permissionDirective } from './directives/permission'

import './styles/variables.css'
import './styles/global.css'
import './styles/animations.css'

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)
app.use(router)
app.directive('permission', permissionDirective)

installGuards(router)

// 启动时从 storage 恢复登录态（不阻塞渲染）
const userStore = useUserStore()
userStore.bootstrap(tokens.getAccessToken(), tokens.getRefreshToken())

// 启动订单超时巡检（待支付订单 15min 自动取消，对齐后端 @Scheduled）
orderStore.startTimeoutWatch()

app.mount('#app')
