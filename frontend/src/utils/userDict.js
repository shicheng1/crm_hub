import { getActiveUsers } from '../api/user'

let cache = null
let cacheTime = 0
const TTL = 5 * 60 * 1000 // 5 分钟

/**
 * 共享用户字典（ID -> User），模块级单例 + TTL 缓存。
 * 替代各页面每次进入都全量拉 /api/users/list，减少重复请求（前端优化 CC-2026-07-07-03）。
 */
export async function getUserDict() {
  const now = Date.now()
  if (cache && now - cacheTime < TTL) {
    return cache
  }
  const res = await getActiveUsers()
  const list = res.data || []
  cache = new Map(list.map(u => [u.id, u]))
  cacheTime = now
  return cache
}

export function invalidateUserDict() {
  cache = null
  cacheTime = 0
}
