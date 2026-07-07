package com.zmd.order.lock;

import com.zmd.order.common.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationAdapter;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.function.Supplier;

/**
 * 分布式锁模板：将「加锁 → 执行 → 释放」的横切逻辑收敛到一处，
 * 业务方法不再手写 try/finally 锁样板（A2 阶段二：横切关注点隔离）。
 *
 * 释放时机修正（P0-3 收口）：默认在 finally 立即释放；若当前处于 Spring 事务中，
 * 则注册 TransactionSynchronization.afterCompletion，在事务「提交/回滚之后」才释放锁，
 * 避免「锁释放早于事务提交 → 并发线程抢锁读到未提交旧状态 → 双审 / 脏读」的隐患。
 * 业务抛异常时立即释放，防止锁泄漏。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LockTemplate {

    private final RedisDistributedLock redisLock;

    public void executeWithLock(String lockKey, long waitSec, long leaseSec,
                                String lockFailedMsg, Runnable action) {
        executeWithLock(lockKey, waitSec, leaseSec, lockFailedMsg, (Supplier<Void>) () -> {
            action.run();
            return null;
        });
    }

    public <T> T executeWithLock(String lockKey, long waitSec, long leaseSec,
                                 String lockFailedMsg, Supplier<T> action) {
        if (!redisLock.tryLock(lockKey, waitSec, leaseSec)) {
            throw new BusinessException(429, lockFailedMsg);
        }
        boolean released = false;
        try {
            T result = action.get();
            // 处于事务中：延迟到事务结束（提交或回滚）后释放，杜绝释放早于提交
            if (TransactionSynchronizationManager.isActualTransactionActive()) {
                TransactionSynchronizationManager.registerSynchronization(
                        new TransactionSynchronizationAdapter() {
                            @Override
                            public void afterCompletion(int status) {
                                redisLock.releaseLock(lockKey);
                            }
                        });
            } else {
                redisLock.releaseLock(lockKey);
                released = true;
            }
            return result;
        } catch (RuntimeException | Error e) {
            if (!released) {
                redisLock.releaseLock(lockKey);
            }
            throw e;
        }
    }
}
