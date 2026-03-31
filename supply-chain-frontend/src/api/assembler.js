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

export function createAssemblyBatch(data) {
  return request({ url: '/assembler/assembly/batch', method: 'post', data })
}

export function getAssemblyBatchList(params) {
  return request({ url: '/assembler/assembly/batch/list', method: 'get', params })
}

export function createAssemblyRecord(data) {
  return request({ url: '/assembler/assembly/record', method: 'post', data })
}

export function getAssemblyRecordList(params) {
  return request({ url: '/assembler/assembly/record/list', method: 'get', params })
}

export function registerAssemblyOnChain(id) {
  return request({ url: `/assembler/assembly/record/${id}/register`, method: 'post' })
}

export function uploadAssemblyReport(data) {
  return request({ url: '/assembler/quality/report', method: 'post', data })
}

export function getAssemblyReportList(params) {
  return request({ url: '/assembler/quality/report/list', method: 'get', params })
}

export function getAssemblerDashboardStats() {
  return request({ url: '/assembler/dashboard/stats', method: 'get' })
}

export function getAssemblerSnTree(sn) {
  return request({ url: '/assembler/dashboard/sn-tree', method: 'get', params: { sn } })
}
