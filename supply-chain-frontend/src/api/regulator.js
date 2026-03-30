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

export function createInspection(data) {
  return request({ url: '/regulator/inspection', method: 'post', data })
}

export function getInspectionList(params) {
  return request({ url: '/regulator/inspection/list', method: 'get', params })
}

export function submitInspectionResult(id, data) {
  return request({ url: `/regulator/inspection/${id}/result`, method: 'put', data })
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
