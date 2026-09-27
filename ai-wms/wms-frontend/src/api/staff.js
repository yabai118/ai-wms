import request from '@/utils/request'

/**
 * 员工档案 API
 *
 * 员工档案是系统的「人员花名册」——账号必须关联到这里的某个人，
 * 所以新增员工是这个系统的入口动作。
 */
export const staffApi = {
  /** 分页查询（带账号情况：已开 / 未开） */
  page(params) {
    return request.get('/staffs', { params })
  },

  /** 新建员工 */
  create(data) {
    return request.post('/staffs', data)
  },

  /** 编辑员工（只能改姓名，工号是登录名不可改） */
  update(id, data) {
    return request.post(`/staffs/${id}`, data)
  },

  /** 在职 / 离职（离职时会连带停用其账号） */
  updateStatus(id, status) {
    return request.post(`/staffs/${id}/status`, null, { params: { status } })
  },

  /** 下拉：在职 + 未开账号的人（供新建账号选人） */
  options() {
    return request.get('/staffs/options')
  }
}
