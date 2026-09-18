import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    port: 5173,
    open: false,
    // 两个后端服务的代理：
    //   /api        -> Java 业务服务（8080）
    //   /agent-api  -> Python Agent 服务（8000）
    //
    // ⚠️ 注意：Python 服务的代理前缀**不能叫 /agent**，
    // 因为前端有个页面路由也叫 /agent，会被代理规则拦截，导致页面 404。
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      '/agent-api': {
        target: 'http://localhost:8000',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/agent-api/, '')
      }
    }
  }
})
