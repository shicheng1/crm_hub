import request from '../utils/request'

export function getStats() {
  return request.get('/api/dashboard/stats')
}

export function getTrend() {
  return request.get('/api/dashboard/trend')
}
