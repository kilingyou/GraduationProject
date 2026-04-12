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

export function getDesignDocFileBlob(id) {
  return request({
    url: `/supplier/design/${id}/file`,
    method: 'get',
    responseType: 'blob'
  })
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

export function parseBomExcel(file) {
  const fd = new FormData()
  fd.append('file', file)
  return request({
    url: '/supplier/bom/import/parse',
    method: 'post',
    data: fd
  })
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

export function getProductionOrderTrack(id) {
  return request({ url: `/supplier/order/${id}/track`, method: 'get' })
}

export function cancelProductionOrder(id) {
  return request({ url: `/supplier/order/${id}/cancel`, method: 'post' })
}

export function listManufacturerOptions() {
  return request({ url: '/supplier/manufacturers', method: 'get' })
}

/** 可选组装商（用户 ID），用于生产订单「指定组装商」 */
export function listAssemblerOptions() {
  return request({ url: '/supplier/assemblers', method: 'get' })
}

/**
 * 指定可领用本单部件的组装商；body.assemblerUserId 为 null 或不传表示不限。
 * @param {number} orderDbId 订单主键 id
 * @param {{ assemblerUserId?: number|null }} body
 */
export function designateAssemblyAssembler(orderDbId, body) {
  return request({
    url: `/supplier/order/${orderDbId}/designate-assembler`,
    method: 'post',
    data: body || {}
  })
}

export function getRejectDispositionList(params) {
  return request({ url: '/supplier/reject-disposition/list', method: 'get', params })
}

export function confirmRejectReturn(data) {
  return request({ url: '/supplier/reject-disposition/confirm-return', method: 'post', data })
}
