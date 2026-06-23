import request from '../utils/request'

export function createOrder(data) {
  return request.post('/api/order/create', data)
}

export function getOrderPage(params) {
  return request.get('/api/order/page', { params })
}

export function getOrderDetail(id) {
  return request.get(`/api/order/detail/${id}`)
}

export function approveOrder(data) {
  return request.post('/api/order/approve', data)
}

export function getTodoList(params) {
  return request.get('/api/order/todo', { params })
}

export function getDoneList(params) {
  return request.get('/api/order/done', { params })
}

export function getOrderLogs(orderId) {
  return request.get(`/api/order/logs/${orderId}`)
}

export function resubmitOrder(orderId) {
  return request.post(`/api/order/resubmit/${orderId}`)
}
