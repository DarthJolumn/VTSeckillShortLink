import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

// 后端网关 8080 / WS 服务 8083（见 md/docs 1.1-项目概览）
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    port: 5173,
    host: true,
    proxy: {
      // 注册接口直连用户服务 8081（绕过网关 8080 便于联调）
      '/api/auth/register': {
        target: 'http://localhost:8081',
        changeOrigin: true,
        rewrite: (p) => p.replace(/^\/api/, '')
      },
      // REST 经网关
      '/api/auth/login': {
        target: 'http://localhost:8081',
        changeOrigin: true,
        rewrite: (p) => p.replace(/^\/api/, '')
      },
      // WebSocket 直连（开发期也可走代理）
      '/ws': {
        target: 'ws://localhost:8083',
        ws: true,
        changeOrigin: true
      }
    }
  }
})
