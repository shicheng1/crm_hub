-- 滑动窗口限流脚本
-- KEYS[1]: 限流 key
-- ARGV[1]: 窗口大小（毫秒）
-- ARGV[2]: 最大请求数
-- ARGV[3]: 当前时间戳（毫秒）

local key = KEYS[1]
local window = tonumber(ARGV[1])
local max_count = tonumber(ARGV[2])
local now = tonumber(ARGV[3])

-- 移除窗口外的旧请求
redis.call('ZREMRANGEBYSCORE', key, 0, now - window)

-- 当前窗口内的请求数
local count = redis.call('ZCARD', key)

if count < max_count then
    -- 未超过限制，记录本次请求
    redis.call('ZADD', key, now, now .. ':' .. math.random(1000000))
    redis.call('PEXPIRE', key, window)
    return 1  -- 允许
else
    return 0  -- 拒绝
end
