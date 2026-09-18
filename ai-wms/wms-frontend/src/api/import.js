import request from '@/utils/request'

export const importApi = {
  /** 下载导入模板（返回 blob） */
  templateUrl(type) {
    return `/api/import/template/${type}`
  },

  /** 上传并导入 */
  upload(type, file) {
    const fd = new FormData()
    fd.append('file', file)
    const urls = {
      product: '/import/products',
      location: '/import/locations',
      inventory: '/import/inventory',
    }
    return request.post(urls[type], fd, {
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 120000,
    })
  }
}
