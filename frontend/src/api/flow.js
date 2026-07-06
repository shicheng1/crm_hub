import request from '../utils/request'

export function getFlowList() {
  return request.get('/api/flow/list')
}

export function getFlowDetail(id) {
  return request.get(`/api/flow/detail/${id}`)
}

export function createFlow(data) {
  return request.post('/api/flow/create', data)
}
