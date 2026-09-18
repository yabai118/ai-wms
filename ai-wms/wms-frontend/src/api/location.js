import request from '@/utils/request'

export const locationApi = {
  /** 分页查询库位 */
  page(params) {
    return request.get('/locations', { params })
  },

  /** 库位地图全量点位 */
  mapPoints(locationType) {
    return request.get('/locations/map', { params: { locationType } })
  },

  /** 库区统计 */
  areas() {
    return request.get('/locations/areas')
  },

  /** 库位类型统计 */
  types() {
    return request.get('/locations/types')
  },

  /** 库位总数 */
  count() {
    return request.get('/locations/count')
  }
}
