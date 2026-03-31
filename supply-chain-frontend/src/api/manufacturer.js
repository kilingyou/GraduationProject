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

export function createBatch(data) {
  return request({ url: '/manufacturer/production/batch', method: 'post', data })
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

export function getManufacturerDashboardStats() {
  return request({ url: '/manufacturer/dashboard/stats', method: 'get' })
}

export function lookupManufacturerDevice(ecid) {
  return request({ url: '/manufacturer/dashboard/device-lookup', method: 'get', params: { ecid } })
}
