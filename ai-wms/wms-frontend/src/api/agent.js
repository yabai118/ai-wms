import axios from 'axios'
import { ElMessage } from 'element-plus'

/**
 * Python Agent 服务客户端
 *
 * 走 vite 代理 /agent -> http://localhost:8000
 * 响应结构和 Java 服务一致（{code, message, data} 风格的简化版）
 */
const agent = axios.create({
  baseURL: '/agent',
  timeout: 30000
})

agent.interceptors.response.use(
  (res) => res.data,
  (err) => {
    const msg = err.response?.data?.detail || 'Agent 服务请求失败'
    ElMessage.error(typeof msg === 'string' ? msg : JSON.stringify(msg))
    return Promise.reject(err)
  }
)

export const agentApi = {
  /** 健康检查 */
  health() {
    return agent.get('/health')
  },

  /** 可用策略列表 */
  strategies() {
    return agent.get('/routing/strategies')
  },

  /** 对某波次跑全部策略并对比 */
  compareWave(waveId) {
    return agent.get(`/routing/wave/${waveId}`)
  },

  /** 按指定策略优化给定任务 */
  optimize(tasks, strategy) {
    return agent.post('/routing/optimize', { tasks, strategy })
  }
}
