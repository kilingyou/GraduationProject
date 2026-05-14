import request from '@/utils/request'

export function scanEcid(data) {
  return request({ url: '/assembler/intake/scan', method: 'post', data })
}

/** multipart：解析 Excel 并返回逐条校验结果 */
export function importVerifyIntake(formData) {
  return request({
    url: '/assembler/intake/import-verify',
    method: 'post',
    data: formData
  })
}

export function downloadIntakeImportTemplate() {
  return request({
    url: '/assembler/intake/import-template',
    method: 'get',
    responseType: 'blob'
  })
}

/** 与扫码验证通过条件一致的可选 ECID（质检合格、已上链、未绑定整机）；params.orderId 按订单过滤 */
export function getAvailableIntakeEcids(params) {
  return request({ url: '/assembler/intake/available-ecids', method: 'get', params })
}

/** 创建组装批次可选：未撤销且本组装商有权组装的订单 */
export function getEligibleAssemblyOrders() {
  return request({ url: '/assembler/assembly/batch/eligible-orders', method: 'get' })
}

export function createAssemblyBatch(data) {
  return request({ url: '/assembler/assembly/batch', method: 'post', data })
}

export function getAssemblyBatchList(params) {
  return request({ url: '/assembler/assembly/batch/list', method: 'get', params })
}

/** multipart：batchNo、firmwareVersion、ecidList（可多次 append）、可选 sn、qualityReport 文件 */
export function createAssemblyRecord(formData) {
  return request({ url: '/assembler/assembly/record', method: 'post', data: formData })
}

export function getAssemblyRecordList(params) {
  return request({ url: '/assembler/assembly/record/list', method: 'get', params })
}

/** 导出 SN 列 Excel，与分销商 SN 批量发货模板格式一致（表头 SN） */
export function exportAssemblySnShipFormat(params) {
  return request({
    url: '/assembler/assembly/record/export-sn-xlsx',
    method: 'get',
    params,
    responseType: 'blob'
  })
}

export function registerAssemblyOnChain(id) {
  return request({ url: `/assembler/assembly/record/${id}/register`, method: 'post' })
}

export function getAssemblerDashboardStats() {
  return request({ url: '/assembler/dashboard/stats', method: 'get' })
}

export function getAssemblerSnTree(sn) {
  return request({ url: '/assembler/dashboard/sn-tree', method: 'get', params: { sn } })
}
