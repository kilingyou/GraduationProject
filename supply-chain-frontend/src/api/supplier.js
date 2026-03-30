import request from '@/utils/request'

export function uploadDesignDoc(data) {
  return request({
    url: '/supplier/design/upload',
    method: 'post',
    data,
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export function getDesignDocList(params) {
  return request({ url: '/supplier/design/list', method: 'get', params })
}

export function getDesignDocDetail(id) {
  return request({ url: `/supplier/design/${id}`, method: 'get' })
}

export function verifyDesignDoc(id) {
  return request({ url: `/supplier/design/${id}/verify`, method: 'post' })
}

export function deleteDesignDoc(id) {
  return request({ url: `/supplier/design/${id}`, method: 'delete' })
}

export function createBom(data) {
  return request({ url: '/supplier/bom', method: 'post', data })
}

export function getBomList(params) {
  return request({ url: '/supplier/bom/list', method: 'get', params })
}

export function getBomDetail(id) {
  return request({ url: `/supplier/bom/${id}`, method: 'get' })
}

export function deleteBom(id) {
  return request({ url: `/supplier/bom/${id}`, method: 'delete' })
}

export function createProductionOrder(data) {
  return request({ url: '/supplier/order', method: 'post', data })
}

export function getProductionOrderList(params) {
  return request({ url: '/supplier/order/list', method: 'get', params })
}

export function getProductionOrderDetail(id) {
  return request({ url: `/supplier/order/${id}`, method: 'get' })
}
