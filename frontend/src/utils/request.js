import axios from 'axios'
import { getToken, removeToken } from './auth'
import { ElMessage } from 'element-plus'
import router from '../router'

const request = axios.create({
  baseURL: '',
  timeout: 10000
})

// 请求拦截器：自动携带 token
request.interceptors.request.use(config => {
  const token = getToken()
  if (token) {
    config.headers['Authorization'] = 'Bearer ' + token
  }
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
      removeToken()
      router.push('/login')
      ElMessage.error('登录已过期，请重新登录')
      return Promise.reject(data)
    }
    ElMessage.error(data.msg || '请求失败')
    return Promise.reject(data)
  },
  error => {
    ElMessage.error('网络异常')
    return Promise.reject(error)
  }
)

export default request
