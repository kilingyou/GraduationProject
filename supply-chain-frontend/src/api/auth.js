import request from '@/utils/request'

export function login(data) {
  return request({ url: '/auth/login', method: 'post', data })
}

export function register(data) {
  return request({ url: '/auth/register', method: 'post', data })
}

export function getUserInfo() {
  return request({ url: '/auth/info', method: 'get' })
}

export function getDashboardStats() {
  return request({ url: '/dashboard/stats', method: 'get' })
}
