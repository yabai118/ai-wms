import request from '@/utils/request'

/**
 * 商品管理 API
 *
 * 每个函数对应一个后端接口，页面只调用这里的函数，
 * 不直接写 axios —— 接口变了只改这一个文件。
 */
export const productApi = {
  /** 分页查询商品列表 */
  page(params) {
    return request.get('/products', { params })
  },

  /** 商品详情（含尺码列表） */
  detail(id) {
    return request.get(`/products/${id}`)
  },

  /** 某商品的尺码编码列表 */
  skus(id) {
    return request.get(`/products/${id}/skus`)
  },

  /** 商品款总数 */
  count() {
    return request.get('/products/count')
  }
}
