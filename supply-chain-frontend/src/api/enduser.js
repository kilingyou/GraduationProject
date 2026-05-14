import request from '@/utils/request'

export function traceProduct(sn) {
  return request({ url: `/public/trace/${sn}`, method: 'get' })
}

export function verifyTraceFile(ipfsCid, expectedHash) {
  return request({
    url: '/public/trace/file/verify',
    method: 'get',
    params: { ipfsCid, expectedHash }
  })
}

export function submitComplaint(data) {
  return request({ url: '/enduser/complaint', method: 'post', data })
}

/** body: { sn, customerName?, customerPhone? } — 匿名销售可仅凭 SN 绑定 */
export function bindUserProduct(data) {
  return request({ url: '/enduser/product/bind', method: 'post', data })
}

export function getUserProductList() {
  return request({ url: '/enduser/product/list', method: 'get' })
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
