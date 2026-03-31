import request from '@/utils/request'

export function getAuditList(params) {
  return request({ url: '/regulator/audit/list', method: 'get', params })
}

export function approveSupplier(id) {
  return request({ url: `/regulator/audit/${id}/approve`, method: 'post' })
}

export function rejectSupplier(id, data) {
  return request({ url: `/regulator/audit/${id}/reject`, method: 'post', data })
}

export function getAuditFileBlob(cid) {
  return request({
    url: `/regulator/audit/file/${encodeURIComponent(cid)}`,
    method: 'get',
    responseType: 'blob'
  })
}

export function createInspection(data) {
  return request({ url: '/regulator/inspection', method: 'post', data })
}

export function getInspectionList(params) {
  return request({ url: '/regulator/inspection/list', method: 'get', params })
}

export function submitInspectionResultMultipart(id, formData) {
  return request({ url: `/regulator/inspection/${id}/result`, method: 'put', data: formData })
}

export function createRecallNotice(data) {
  return request({ url: '/regulator/recall', method: 'post', data })
}

export function getRecallNoticeList(params) {
  return request({ url: '/regulator/recall/list', method: 'get', params })
}

export function analyzeRecall(sn) {
  return request({ url: `/regulator/recall/analyze/${sn}`, method: 'get' })
}

export function exportRecallEvidence(sn) {
  return request({ url: `/regulator/recall/evidence/${sn}`, method: 'get' })
}

export function exportRecallEvidencePdf(sn) {
  return request({
    url: `/regulator/recall/evidence/${sn}/pdf`,
    method: 'get',
    responseType: 'blob'
  })
}

export function analyzeAnomaly(sn) {
  return request({ url: `/regulator/recall/anomaly/${sn}`, method: 'get' })
}

export function getRecentAnomalies(params) {
  return request({ url: '/regulator/recall/anomalies/recent', method: 'get', params })
}

export function getRecallSchedulerStatus() {
  return request({ url: '/regulator/recall/scheduler/status', method: 'get' })
}

export function runRecallSchedulerNow() {
  return request({ url: '/regulator/recall/scheduler/run', method: 'post' })
}
