import request from '../utils/request'

export function getUserPage(params) {
  return request.get('/api/users/page', { params })
}

export function getActiveUsers() {
  return request.get('/api/users/list')
}

export function createUser(data) {
  return request.post('/api/users', data)
}

export function updateUser(id, data) {
  return request.put(`/api/users/${id}`, data)
}

export function updateUserStatus(id, status) {
  return request.put(`/api/users/${id}/status`, { status })
}

export function resetUserPassword(id, password) {
  return request.put(`/api/users/${id}/password`, { password })
}
