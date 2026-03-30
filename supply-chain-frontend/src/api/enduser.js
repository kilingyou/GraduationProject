import request from '@/utils/request'

export function traceProduct(sn) {
  return request({ url: `/public/trace/${sn}`, method: 'get' })
}

export function submitComplaint(data) {
  return request({ url: '/enduser/complaint', method: 'post', data })
}

export function getComplaintList(params) {
  return request({ url: '/enduser/complaint/list', method: 'get', params })
}

export function applyDecommission(data) {
  return request({ url: '/enduser/decommission', method: 'post', data })
}

export function getDecommissionList(params) {
  return request({ url: '/enduser/decommission/list', method: 'get', params })
}
