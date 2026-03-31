import request from '@/utils/request'

export function shipProducts(data) {
  return request({ url: '/distributor/logistics/ship', method: 'post', data })
}

export function receiveProducts(data) {
  return request({ url: '/distributor/logistics/receive', method: 'post', data })
}

export function getTransferList(params) {
  return request({ url: '/distributor/logistics/list', method: 'get', params })
}

export function trackProduct(sn) {
  return request({ url: `/distributor/logistics/track/${sn}`, method: 'get' })
}

export function shipBatchProducts(formData) {
  return request({
    url: '/distributor/logistics/ship-batch',
    method: 'post',
    data: formData
  })
}

export function downloadSnShipTemplate() {
  return request({
    url: '/distributor/logistics/sn-import-template',
    method: 'get',
    responseType: 'blob'
  })
}

export function getInventoryList(params) {
  return request({ url: '/distributor/inventory/list', method: 'get', params })
}

export function registerSale(data) {
  return request({ url: '/distributor/sales', method: 'post', data })
}

export function getSalesList(params) {
  return request({ url: '/distributor/sales/list', method: 'get', params })
}
