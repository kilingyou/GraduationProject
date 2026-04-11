import request from '@/utils/request'

export function getUserList(params) {
  return request({ url: '/system/user/list', method: 'get', params })
}

export function updateUser(id, data) {
  return request({ url: `/system/user/${id}`, method: 'put', data })
}

export function deleteUser(id) {
  return request({ url: `/system/user/${id}`, method: 'delete' })
}

export function toggleUserStatus(id, status) {
  return request({ url: `/system/user/${id}/status`, method: 'put', params: { status } })
}

export function assignUserRole(id, roleKey) {
  return request({ url: `/system/user/${id}/role`, method: 'put', params: { roleKey } })
}

export function getRoleConsistency(params) {
  return request({ url: '/system/user/role-consistency', method: 'get', params })
}

export function repairRoleConsistency(params) {
  return request({ url: '/system/user/role-consistency/repair', method: 'post', params })
}

export function initUserBlockchainAccount(id) {
  return request({ url: `/system/user/${id}/blockchain-account/init`, method: 'put' })
}

export function getRoleList() {
  return request({ url: '/system/role/list', method: 'get' })
}

export function assignMenus(roleId, menuIds) {
  return request({ url: `/system/role/menu/${roleId}`, method: 'post', data: menuIds })
}

export function getMenuTree() {
  return request({ url: '/system/menu/tree', method: 'get' })
}

export function getMenuList() {
  return request({ url: '/system/menu/list', method: 'get' })
}

export function addMenu(data) {
  return request({ url: '/system/menu', method: 'post', data })
}

export function updateMenu(id, data) {
  return request({ url: `/system/menu/${id}`, method: 'put', data })
}

export function deleteMenu(id) {
  return request({ url: `/system/menu/${id}`, method: 'delete' })
}

export function getLogList(params) {
  return request({ url: '/system/log/list', method: 'get', params })
}
