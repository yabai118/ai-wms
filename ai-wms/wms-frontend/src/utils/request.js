import axios from 'axios'
import { ElMessage } from 'element-plus'
import { getToken, clearLogin } from '@/utils/authStorage'

/**
 * axios 封装
 *
 * 统一处理：
 * ① 请求前缀 / 超时
 * ② 响应拦截：后端返回 {code, message, data}
 *    - code === 200  → 直接把 data 返回给业务代码
 *    - 其他           → 弹出错误提示并 reject
 * ③ 网络错误统一提示
 */
const request = axios.create({
  baseURL: '/api',        // 由 vite proxy 转发到后端
  timeout: 15000
})

// ---------- 请求拦截器 ----------
request.interceptors.request.use(
  (config) => {
    // 每个请求自动带上登录凭证；后端 AuthInterceptor 从这里取 token
    const token = getToken()
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

/**
 * 登录失效的统一处理
 *
 * ⚠️ 两处都要调它：这个项目的业务异常走 **HTTP 200 + body 里的 code**，
 * 但拦截器抛的 401 走**真实的 HTTP 401**。两条路径都得处理，
 * 否则会出现「提示了未登录，但凭证没清、页面也没跳」的卡死状态。
 */
function handleUnauthorized(message) {
  ElMessage.error(message || '登录已过期，请重新登录')
  clearLogin()
  // 已经在登录页就别再跳，否则会来回刷
  if (!window.location.pathname.startsWith('/login')) {
    // 用整页跳转而不是 router.push：这里不引 router，
    // 免得形成 router → utils/request → router 的循环依赖
    window.location.href = '/login'
  }
}

// ---------- 响应拦截器 ----------
request.interceptors.response.use(
  (response) => {
    const res = response.data

    // 后端统一返回 {code, message, data}
    if (res.code === 200) {
      return res.data          // 业务代码直接用 data，不用每次 .data.data
    }

    // ★ 业务层返回的 401（如 /auth/me 发现 token 里的用户已不存在）——
    //   它走的是 HTTP 200，所以下面的 error 分支接不到，必须在这里处理
    if (res.code === 401) {
      handleUnauthorized(res.message)
      return Promise.reject(new Error(res.message || '未登录'))
    }

    // 其他业务失败
    ElMessage.error(res.message || '请求失败')
    return Promise.reject(new Error(res.message || '请求失败'))
  },
  (error) => {
    const status = error.response?.status
    // 后端在 401/403 时会返回 {code, message}，优先用它的文案
    const serverMsg = error.response?.data?.message

    // ---- 未登录 / 登录过期（拦截器抛的真实 HTTP 401）----
    if (status === 401) {
      handleUnauthorized(serverMsg)
      return Promise.reject(error)
    }

    let msg = serverMsg || '网络异常，请稍后重试'
    if (error.code === 'ECONNABORTED') {
      msg = '请求超时'
    } else if (error.response && !serverMsg) {
      const map = {
        400: '请求参数错误',
        403: '没有权限执行该操作',
        404: '请求的资源不存在',
        500: '服务器内部错误'
      }
      msg = map[status] || `请求失败 (${status})`
    }
    ElMessage.error(msg)
    return Promise.reject(error)
  }
)

export default request
