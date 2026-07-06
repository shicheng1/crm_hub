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

function resolvePendingQueue(token) {
  pendingQueue.forEach(callback => callback(token))
  pendingQueue = []
}

function redirectToLogin(message = '登录已过期，请重新登录') {
  removeToken()
  router.push('/login')
  ElMessage.error(message)
}

async function tryRefreshToken(originalRequest) {
  const refreshToken = getRefreshToken()
  if (!refreshToken || originalRequest._retry) {
    redirectToLogin()
    return Promise.reject(originalRequest)
  }

  originalRequest._retry = true

  if (refreshing) {
    return new Promise(resolve => {
      pendingQueue.push(token => {
        originalRequest.headers.Authorization = 'Bearer ' + token
        resolve(request(originalRequest))
      })
    })
  }

  refreshing = true
  try {
    const res = await axios.post('/auth/refresh', { refreshToken })
    if (res.data.code !== 200 || !res.data.data?.token) {
      redirectToLogin(res.data.msg || '登录已过期，请重新登录')
      return Promise.reject(res.data)
    }
    const newToken = res.data.data.token
    setToken(newToken)
    resolvePendingQueue(newToken)
    originalRequest.headers.Authorization = 'Bearer ' + newToken
    return request(originalRequest)
  } catch (e) {
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

export default request
