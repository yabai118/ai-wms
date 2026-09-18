import request from '@/utils/request'

export const inboundApi = {
  /** 分页查询入库单 */
  page(params) {
    return request.get('/inbound-orders', { params })
  },

  /** 入库单详情（含明细） */
  detail(id) {
    return request.get(`/inbound-orders/${id}`)
  },

  /** 创建入库单 */
  create(data) {
    return request.post('/inbound-orders', data)
  },

  /** 收货 */
  receive(id, data) {
    return request.post(`/inbound-orders/${id}/receive`, data)
  },

  /** 上架 */
  shelve(id, data) {
    return request.post(`/inbound-orders/${id}/shelve`, data)
  },

  /** 推荐货位 */
  recommend(skuId, qty = 1, count = 3) {
    return request.get('/inbound-orders/recommend', { params: { skuId, qty, count } })
  }
}
