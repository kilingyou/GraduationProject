import request from '@/utils/request'

export function scanEcid(data) {
  return request({ url: '/assembler/intake/scan', method: 'post', data })
}

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
