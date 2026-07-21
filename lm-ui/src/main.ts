import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import './router/guard'
import './assets/styles/variables.css'

async function bootstrap() {
  // DEV 且未显式关闭时启动 MSW（联调真实后端时设 VITE_USE_MSW=false）
  if (import.meta.env.DEV && import.meta.env.VITE_USE_MSW !== 'false') {
    const { worker } = await import('./mocks/browser')
    await worker.start({ onUnhandledRequest: 'bypass' })
  }

  const app = createApp(App)
  app.use(createPinia())
  app.use(router)
  app.mount('#app')
}

bootstrap()
