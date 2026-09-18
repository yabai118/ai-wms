import request from '@/utils/request'

export const healthApi = {
  /** 服务探活 */
  check() {
    return request.get('/health')
  }
}
