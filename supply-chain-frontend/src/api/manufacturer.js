import request from '@/utils/request'

export function getOrderList(params) {
  return request({ url: '/manufacturer/order/list', method: 'get', params })
}

export function acceptOrder(orderId, data) {
  return request({ url: `/manufacturer/order/${orderId}/accept`, method: 'post', data })
}

export function getAgreement(orderId) {
  return request({ url: `/manufacturer/order/${orderId}/agreement`, method: 'get' })
}

/** 经鉴权从后端拉取设计文件（不直连 IPFS 网关） */
export function getManufacturerOrderDesignFileBlob(orderId) {
  return request({
    url: `/manufacturer/order/${orderId}/design-file`,
    method: 'get',
    responseType: 'blob'
  })
}

export function createBatch(data) {
  return request({ url: '/manufacturer/production/batch', method: 'post', data })
}

/** 已接单订单的 BOM 明细行（用于创建子件批次） */
export function getOrderBomItemsForProduction(orderId) {
  return request({ url: `/manufacturer/production/order/${orderId}/bom-items`, method: 'get' })
}

export function completeProductionBatch(batchId) {
  return request({ url: '/manufacturer/production/batch/complete', method: 'post', data: { batchId } })
}

export function getBatchList(params) {
  return request({ url: '/manufacturer/production/batch/list', method: 'get', params })
}

export function generateEcids(data) {
  return request({ url: '/manufacturer/production/ecid/generate', method: 'post', data })
}

export function getEcidList(params) {
  return request({ url: '/manufacturer/production/ecid/list', method: 'get', params })
}

/** 批量注册：传 { ids: number[] } 或 { ecids: string[] } */
export function registerEcids(data) {
  return request({ url: '/manufacturer/production/ecid/register', method: 'post', data })
}

/** 放行给组装商：body 为 { batchId } 或 { ecids: string[] }，仅质检合格且已上链的部件会计数 */
export function releasePartsToAssembler(data) {
  return request({ url: '/manufacturer/production/ecid/release-to-assembler', method: 'post', data })
}

export function uploadQualityReport(data) {
  return request({ url: '/manufacturer/quality/report', method: 'post', data })
}

export function completeProduction(data) {
  return request({ url: '/manufacturer/quality/complete', method: 'post', data })
}

export function rejectDevice(data) {
  return request({ url: '/manufacturer/quality/reject', method: 'post', data })
}

export function getQualityReportList(params) {
  return request({ url: '/manufacturer/quality/report/list', method: 'get', params })
}

export function getRejectRecordList(params) {
  return request({ url: '/manufacturer/quality/reject-record/list', method: 'get', params })
}

export function confirmRejectDestroy(data) {
  return request({ url: '/manufacturer/quality/reject-record/confirm-destroy', method: 'post', data })
}

export function getManufacturerDashboardStats() {
  return request({ url: '/manufacturer/dashboard/stats', method: 'get' })
}

export function lookupManufacturerDevice(ecid) {
  return request({ url: '/manufacturer/dashboard/device-lookup', method: 'get', params: { ecid } })
}
