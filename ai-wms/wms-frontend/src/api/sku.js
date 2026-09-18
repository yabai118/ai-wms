import request from '@/utils/request'

export const skuApi = {
  /** 搜索 SKU（按 SKU 编码或款号，用于创建入库单时选择商品） */
  search(keyword, limit = 20) {
    return request.get('/skus/search', { params: { keyword, limit } })
  },

  /** 某商品的尺码编码列表 */
  listByProduct(productId) {
    return request.get(`/products/${productId}/skus`)
  }
}
