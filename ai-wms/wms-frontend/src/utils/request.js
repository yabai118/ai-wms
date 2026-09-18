import axios from 'axios'
import { ElMessage } from 'element-plus'

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
    // 预留：以后可以在这里加 token
    // const token = localStorage.getItem('token')
    // if (token) config.headers.Authorization = `Bearer ${token}`
    return config
  },
  (error) => Promise.reject(error)
)

// ---------- 响应拦截器 ----------
request.interceptors.response.use(
  (response) => {
    const res = response.data

    // 后端统一返回 {code, message, data}
    if (res.code === 200) {
      return res.data          // 业务代码直接用 data，不用每次 .data.data
    }

    // 业务失败
    ElMessage.error(res.message || '请求失败')
    return Promise.reject(new Error(res.message || '请求失败'))
  },
  (error) => {
    // 网络层错误
    let msg = '网络异常，请稍后重试'
    if (error.code === 'ECONNABORTED') {
      msg = '请求超时'
    } else if (error.response) {
      const status = error.response.status
      const map = {
        400: '请求参数错误',
        401: '未登录或登录已过期',
        403: '没有权限',
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
