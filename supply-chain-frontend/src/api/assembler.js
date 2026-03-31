import request from '@/utils/request'

export function scanEcid(data) {
  return request({ url: '/assembler/intake/scan', method: 'post', data })
}

/** JSON：{ ecids: string[] }，返回逐条 IntakeVerifyResult */
export function verifyIntakeBatch(data) {
  return request({ url: '/assembler/intake/verify-batch', method: 'post', data })
}

/** multipart：file，返回解析出的 ecid 字符串列表 */
export function parseIntakeImport(formData) {
  return request({
    url: '/assembler/intake/import-parse',
    method: 'post',
    data: formData
  })
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

/** 兼容：仅 JSON 批量校验（无 Excel） */
export function batchImportEcids(data) {
  return request({ url: '/assembler/intake/batch-import', method: 'post', data })
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
