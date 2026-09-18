import request from '@/utils/request'

export const inventoryApi = {
  /** 分页查询库存（五字段） */
  page(params) {
    return request.get('/inventory', { params })
  },

  /** 库存流水 */
  transactions(params) {
    return request.get('/inventory/transactions', { params })
  },

  /** 库存总览统计 */
  summary() {
    return request.get('/inventory/summary')
  },

  /** ★ 库存对账 */
  reconcile() {
    return request.get('/inventory/reconcile')
  },

  /** 冻结 */
  freeze(id, qty, reason) {
    return request.post(`/inventory/${id}/freeze`, null, { params: { qty, reason } })
  },

  /** 解冻 */
  unfreeze(id, qty) {
    return request.post(`/inventory/${id}/unfreeze`, null, { params: { qty } })
  }
}

/** 导出库存（浏览器直接下载） */
export function exportInventoryUrl(params) {
  const qs = new URLSearchParams(
    Object.entries(params || {}).filter(([, v]) => v !== null && v !== undefined && v !== '')
  ).toString()
  return `/api/export/inventory${qs ? '?' + qs : ''}`
}
