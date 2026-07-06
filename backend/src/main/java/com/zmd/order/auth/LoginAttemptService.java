package com.zmd.order.auth;

import com.zmd.order.common.Constants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 登录尝试服务
 *
 * 防暴力破解：连续失败超过阈值后锁定账号一段时间
 * 使用 Redis 实现，支持分布式环境
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    private final StringRedisTemplate redisTemplate;

    /**
     * 检查账号是否被锁定
     */
    public boolean isLocked(String username) {
        String key = Constants.LOGIN_LOCK_PREFIX + username;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    /**
     * 记录一次失败尝试，返回当前失败次数
     */
    public int recordFailedAttempt(String username) {
        String failKey = Constants.LOGIN_FAIL_PREFIX + username;
        Long count = redisTemplate.opsForValue().increment(failKey);
        if (count != null && count == 1) {
            // 第一次失败时设置过期时间（与锁定时间一致）
            redisTemplate.expire(failKey, Constants.LOGIN_LOCK_MINUTES, TimeUnit.MINUTES);
        }

        if (count != null && count >= Constants.LOGIN_MAX_FAIL) {
            // 达到阈值，锁定账号
            String lockKey = Constants.LOGIN_LOCK_PREFIX + username;
            redisTemplate.opsForValue().set(lockKey, "1", Constants.LOGIN_LOCK_MINUTES, TimeUnit.MINUTES);
            log.warn("账号被锁定: username={}, failCount={}", username, count);
        }

        return count != null ? count.intValue() : 0;
    }

    /**
     * 登录成功后清除失败计数
     */
    public void clearAttempts(String username) {
        redisTemplate.delete(Constants.LOGIN_FAIL_PREFIX + username);
    }

    /**
     * 获取剩余尝试次数
     */
    public int getRemainingAttempts(String username) {
        String failKey = Constants.LOGIN_FAIL_PREFIX + username;
        String count = redisTemplate.opsForValue().get(failKey);
        if (count == null) {
            return Constants.LOGIN_MAX_FAIL;
        }
        int used = Integer.parseInt(count);
        return Math.max(0, Constants.LOGIN_MAX_FAIL - used);
    }
}
