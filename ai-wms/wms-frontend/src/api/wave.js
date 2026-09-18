import request from '@/utils/request'

export const waveApi = {
  /** 分页查询波次 */
  page(params) {
    return request.get('/waves', { params })
  },

  /** 波次详情（含拣货任务） */
  detail(id) {
    return request.get(`/waves/${id}`)
  },

  /** ★ 生成波次 */
  generate(orderIds, operatorId) {
    return request.post('/waves/generate', { orderIds, operatorId })
  },

  /** ★ 拣货确认 */
  pick(id) {
    return request.post(`/waves/${id}/pick`)
  },

  /** ★ 发货确认 */
  ship(id) {
    return request.post(`/waves/${id}/ship`)
  },

  /** 波次拣货任务 */
  tasks(id) {
    return request.get(`/waves/${id}/tasks`)
  },

  /** 行走距离 */
  distance(id) {
    return request.get(`/waves/${id}/distance`)
  }
}

/** 导出拣货单（浏览器直接下载） */
export function exportPickTasksUrl(waveId) {
  return `/api/export/wave/${waveId}/tasks`
}
