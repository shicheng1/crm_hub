import request from '../utils/request'
import { getActiveUsers } from './user'

export function getDeptList() {
  return request.get('/api/dept/list')
}

export function getUserList() {
  return getActiveUsers()
}
