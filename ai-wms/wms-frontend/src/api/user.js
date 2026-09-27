import request from '@/utils/request'

export const userApi = {
  /** 分页查询账号 */
  page(params) {
    return request.get('/users', { params })
  },

  /** 新建账号 */
  create(data) {
    return request.post('/users', data)
  },

  /** 启用 / 停用（用停用代替删除——单据引用了操作人，删了审计链就断） */
  updateStatus(id, status) {
    // POST 带 query 参数时第二参传 null（与项目里 freeze/unfreeze 的写法一致）
    return request.post(`/users/${id}/status`, null, { params: { status } })
  },

  /** 重置他人密码（无需原密码，管理员本来就不知道） */
  resetPassword(id, newPassword) {
    return request.post(`/users/${id}/reset-password`, { newPassword })
  },

  /** 员工下拉（新建账号时选「这个账号对应哪个员工」） */
  staff() {
    return request.get('/users/staff')
  }
}
