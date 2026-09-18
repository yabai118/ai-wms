import axios from 'axios'
import { ElMessage } from 'element-plus'

/**
 * Python Agent 服务客户端
 *
 * 走 vite 代理 /agent-api -> http://localhost:8000
 *
 * ⚠️ 前缀是 /agent-api 不是 /agent——
 * 因为前端页面路由也叫 /agent，用 /agent 做代理前缀会拦截页面请求。
 */
const agent = axios.create({
  baseURL: '/agent-api',
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
  },

  // ---------- 编排器 ----------
  /** 列出编排器支持的任务类型 */
  orchestrateTypes() {
    return agent.get('/orchestrate/types')
  },

  /** 提交任务给编排器（自动路由 + 校验 + 降级） */
  orchestrate(taskType, payload) {
    return agent.post('/orchestrate', { taskType, payload })
  },

  /** ★ 编排器运行统计（监控面板用） */
  orchestrateStats() {
    return agent.get('/orchestrate/stats')
  },

  // ---------- LLM Agent ----------
  /** LLM 是否可用 */
  llmStatus() {
    return agent.get('/llm/status')
  },

  /** 自然语言查询（Function Calling） */
  nlQuery(question) {
    return agent.post('/llm/query', { question })
  },

  /** LLM 异常解释 */
  explainAnomaly(anomalyType, detail) {
    return agent.post('/llm/explain-anomaly', { anomalyType, detail })
  }
}
