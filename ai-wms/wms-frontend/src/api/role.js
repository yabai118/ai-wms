import request from '@/utils/request'

/**
 * 角色管理 API
 *
 * ★ 这组接口是「加角色不用改代码」的入口。
 */
export const roleApi = {
  /** 角色列表（含每个角色的权限点和在用账号数） */
  list() {
    return request.get('/roles')
  },

  /** 权限点清单（code → 中文说明），供勾选框渲染 */
  permissions() {
    return request.get('/roles/permissions')
  },

  /** 新建角色 */
  create(data) {
    return request.post('/roles', data)
  },

  /** 修改角色的权限分配（全量覆盖，改完立即生效） */
  updatePermissions(code, permissions) {
    return request.post(`/roles/${code}/permissions`, { permissions })
  },

  /** 启用 / 停用角色 */
  updateStatus(code, status) {
    return request.post(`/roles/${code}/status`, null, { params: { status } })
  }
}
