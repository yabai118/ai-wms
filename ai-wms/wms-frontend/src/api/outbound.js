import request from '@/utils/request'

export const outboundApi = {
  /** 分页查询出库单 */
  page(params) {
    return request.get('/outbound-orders', { params })
  },

  /** 出库单详情（含明细与分配情况） */
  detail(id) {
    return request.get(`/outbound-orders/${id}`)
  },

  /** ★ 分配库存 */
  allocate(id) {
    return request.post(`/outbound-orders/${id}/allocate`)
  },

  /** 查询分配明细 */
  allocations(id) {
    return request.get(`/outbound-orders/${id}/allocations`)
  }
}

/** 导出订单（浏览器直接下载） */
export function exportOrdersUrl(params) {
  const qs = new URLSearchParams(
    Object.entries(params || {}).filter(([, v]) => v !== null && v !== undefined && v !== '')
  ).toString()
  return `/api/export/orders${qs ? '?' + qs : ''}`
}
