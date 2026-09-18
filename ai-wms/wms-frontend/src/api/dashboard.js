import request from '@/utils/request'

export const dashboardApi = {
  /** 看板全部数据（一次请求返回） */
  get() {
    return request.get('/dashboard')
  }
}
