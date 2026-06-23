import request from '../utils/request'

export function getDeptList() {
  return request.get('/api/dept/list')
}

export function getUserList() {
  return request.get('/api/dept/users')
}
