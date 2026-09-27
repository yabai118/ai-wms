import request from '@/utils/request'

export const authApi = {
  /** 登录（成功返回 { token, staffId, staffCode, staffName, role, roleName, permissions }） */
  login(data) {
    return request.post('/auth/login', data)
  },

  /** 当前登录用户——刷新页面后用它确认 token 还有效，并拿到最新权限 */
  me() {
    return request.get('/auth/me')
  },

  /**
   * 修改自己的密码
   *
   * 成功后服务端会撤销此前签发的所有令牌，所以调用方应当清凭证并回登录页。
   */
  changePassword(data) {
    return request.post('/auth/change-password', data)
  }
}

// 登录态的读写统一放在 @/utils/authStorage —— 那里不依赖任何模块，
// 避免 router → api/auth → utils/request → router 的循环依赖
export { getToken, setLogin, getUser, getPermissions, clearLogin } from '@/utils/authStorage'
