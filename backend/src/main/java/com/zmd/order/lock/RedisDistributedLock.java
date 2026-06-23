package com.zmd.order.lock;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Redis 分布式锁实现（面试核心亮点）
 *
 * 实现原理：
 * 1. tryLock: 使用 SET key value NX EX 原子命令，保证互斥性
 * 2. release: 使用 Lua 脚本，先比较 value 再删除，保证只有锁持有者能释放
 * 3. value 使用 UUID，防止误删其他线程的锁
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisDistributedLock {

    private final StringRedisTemplate redisTemplate;
    private DefaultRedisScript<Long> releaseScript;

    private static final String LOCK_VALUE_PREFIX = UUID.randomUUID().toString();

    @PostConstruct
    public void init() {
        releaseScript = new DefaultRedisScript<>();
        releaseScript.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/lock_release.lua")));
        releaseScript.setResultType(Long.class);
    }

    /**
     * 尝试获取锁
     *
     * @param lockKey     锁的 key
     * @param waitTime    最大等待时间（秒）
     * @param expireTime  锁过期时间（秒）
     * @return true=获取成功, false=获取失败
     */
    public boolean tryLock(String lockKey, long waitTime, long expireTime) {
        String value = LOCK_VALUE_PREFIX + ":" + Thread.currentThread().getId();
        long deadline = System.currentTimeMillis() + waitTime * 1000;

        while (System.currentTimeMillis() < deadline) {
            // SET key value NX EX expireTime —— 原子操作
            Boolean success = redisTemplate.opsForValue()
                    .setIfAbsent(lockKey, value, expireTime, TimeUnit.SECONDS);
            if (Boolean.TRUE.equals(success)) {
                log.debug("获取分布式锁成功, key={}, threadId={}", lockKey, Thread.currentThread().getId());
                return true;
            }
            // 短暂等待后重试
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        log.warn("获取分布式锁超时, key={}, waitTime={}s", lockKey, waitTime);
        return false;
    }

    /**
     * 释放锁（Lua 脚本保证原子性）
     *
     * Lua 脚本逻辑：
     * if redis.call('get', KEYS[1]) == ARGV[1] then
     *     return redis.call('del', KEYS[1])
     * else
     *     return 0
     * end
     */
    public void releaseLock(String lockKey) {
        String value = LOCK_VALUE_PREFIX + ":" + Thread.currentThread().getId();
        Long result = redisTemplate.execute(releaseScript, Collections.singletonList(lockKey), value);
        if (result != null && result > 0) {
            log.debug("释放分布式锁成功, key={}", lockKey);
        } else {
            log.warn("释放分布式锁失败(锁已过期或不属于当前线程), key={}", lockKey);
        }
    }
}
