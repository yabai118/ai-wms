import request from '@/utils/request'

export const authApi = {
  /** 登录（成功返回 { token, username, displayName, role, roleName, operatorId }） */
  login(data) {
    return request.post('/auth/login', data)
  },

  /** 当前登录用户——刷新页面后用它确认 token 还有效 */
  me() {
    return request.get('/auth/me')
  }
}

// 登录态的读写统一放在 @/utils/authStorage —— 那里不依赖任何模块，
// 避免 router → api/auth → utils/request → router 的循环依赖
export { getToken, setLogin, getUser, clearLogin } from '@/utils/authStorage'
