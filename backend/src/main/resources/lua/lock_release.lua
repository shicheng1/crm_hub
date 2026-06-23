-- 分布式锁释放脚本
-- KEYS[1]: 锁的 key
-- ARGV[1]: 锁的 value（包含 UUID + threadId，保证只有持有者能释放）
-- 返回: 1=释放成功, 0=锁不属于当前线程或已过期

if redis.call('get', KEYS[1]) == ARGV[1] then
    return redis.call('del', KEYS[1])
else
    return 0
end
