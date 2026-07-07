import axios from 'axios'
import { getRefreshToken, getToken, removeToken, setToken } from './auth'
import { ElMessage } from 'element-plus'
import router from '../router'

const request = axios.create({
  baseURL: '',
  timeout: 10000
})

let refreshing = false
let pendingQueue = []

// 并发相同 GET 请求去重：相同 method+url+params 共享同一个 Promise，避免路由切换/多组件重复拉取
const inflight = new Map()

function inflightKey(config) {
  const { method, url, params, data } = config
  return `${String(method || 'get').toUpperCase()}|${url}|${JSON.stringify(params || {})}|${JSON.stringify(data || {})}`
}

function resolvePendingQueue(token) {
  const queue = pendingQueue
  pendingQueue = []
  queue.forEach(callback => callback(token))
}

// 刷新失败时清空等待队列：回调收到 null 直接 reject。
// 否则并发 401 的请求会永久 pending（P0 修复：原实现只 reject 当前请求，队列永不触发）
function rejectPendingQueue() {
  const queue = pendingQueue
  pendingQueue = []
  queue.forEach(callback => callback(null))
}

function redirectToLogin(message = '登录已过期，请重新登录') {
  removeToken()
  router.push('/login')
  ElMessage.error(message)
}

async function tryRefreshToken(originalRequest) {
  const refreshToken = getRefreshToken()
  if (!refreshToken || originalRequest._retry) {
    return Promise.reject(originalRequest)
  }

  originalRequest._retry = true

  if (refreshing) {
    // 已有刷新在进行：排队，刷新成功后由 resolvePendingQueue 统一重放
    return new Promise((resolve, reject) => {
      pendingQueue.push(token => {
        if (!token) {
          reject(originalRequest)
          return
        }
        originalRequest.headers.Authorization = 'Bearer ' + token
        resolve(request(originalRequest))
      })
    })
  }

  refreshing = true
  try {
    const res = await axios.post('/auth/refresh', { refreshToken })
    if (res.data.code !== 200 || !res.data.data?.token) {
      rejectPendingQueue()
      redirectToLogin(res.data?.msg || '登录已过期，请重新登录')
      return Promise.reject(res.data)
    }
    const newToken = res.data.data.token
    setToken(newToken)
    resolvePendingQueue(newToken)
    originalRequest.headers.Authorization = 'Bearer ' + newToken
    return request(originalRequest)
  } catch (e) {
    rejectPendingQueue()
    redirectToLogin('登录已过期，请重新登录')
    return Promise.reject(e)
  } finally {
    refreshing = false
  }
}

// 请求拦截器：自动携带 token 和 traceId
request.interceptors.request.use(config => {
  const token = getToken()
  if (token) {
    config.headers['Authorization'] = 'Bearer ' + token
  }
  config.headers['X-Trace-Id'] = crypto.randomUUID ? crypto.randomUUID().replaceAll('-', '') : String(Date.now())
  return config
})

// 响应拦截器：统一错误处理
request.interceptors.response.use(
  response => {
    const { data } = response
    if (data.code === 200) {
      return data
    }
    if (data.code === 401) {
      return tryRefreshToken(response.config)
    }
    if (data.code === 403) {
      ElMessage.error(data.msg || '权限不足')
      return Promise.reject(data)
    }
    ElMessage.error(data.msg || '请求失败')
    return Promise.reject(data)
  },
  error => {
    // 处理 HTTP 状态码（后端返回真实 401/403）
    if (error.response) {
      const status = error.response.status
      const data = error.response.data
      if (status === 401) {
        return tryRefreshToken(error.config)
      } else if (status === 403) {
        ElMessage.error(data?.msg || '权限不足')
      } else {
        ElMessage.error(data?.msg || `请求错误 (${status})`)
      }
    } else {
      ElMessage.error('网络异常')
    }
    return Promise.reject(error)
  }
)

// GET 请求去重（包一层核心 request，拦截器仍生效）
const coreRequest = request.request.bind(request)
request.request = (config) => {
  if (String(config.method || 'get').toUpperCase() === 'GET') {
    const key = inflightKey(config)
    if (inflight.has(key)) {
      return inflight.get(key)
    }
    const p = coreRequest(config)
    inflight.set(key, p)
    p.finally(() => inflight.delete(key))
    return p
  }
  return coreRequest(config)
}

export default request
