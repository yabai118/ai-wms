import request from '@/utils/request'

export const customerApi = {
  /** 查询客户（下拉选择用，keyword 匹配编码或名称） */
  search(keyword, limit) {
    return request.get('/customers', { params: { keyword, limit } })
  }
}
