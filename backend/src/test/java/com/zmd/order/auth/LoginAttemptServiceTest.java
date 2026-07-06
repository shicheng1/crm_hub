package com.zmd.order.auth;

import com.zmd.order.common.Constants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("登录尝试服务测试")
class LoginAttemptServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOps;

    @InjectMocks
    private LoginAttemptService loginAttemptService;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    @Test
    @DisplayName("未锁定账号 - isLocked 返回 false")
    void shouldNotBeLocked_whenNoLockKey() {
        when(redisTemplate.hasKey(Constants.LOGIN_LOCK_PREFIX + "testuser")).thenReturn(false);
        assertFalse(loginAttemptService.isLocked("testuser"));
    }

    @Test
    @DisplayName("已锁定账号 - isLocked 返回 true")
    void shouldBeLocked_whenLockKeyExists() {
        when(redisTemplate.hasKey(Constants.LOGIN_LOCK_PREFIX + "testuser")).thenReturn(true);
        assertTrue(loginAttemptService.isLocked("testuser"));
    }

    @Test
    @DisplayName("第一次失败 - 设置过期时间")
    void shouldSetExpiry_onFirstFail() {
        when(valueOps.increment(Constants.LOGIN_FAIL_PREFIX + "testuser")).thenReturn(1L);
        int count = loginAttemptService.recordFailedAttempt("testuser");
        assertEquals(1, count);
        verify(valueOps).increment(Constants.LOGIN_FAIL_PREFIX + "testuser");
        verify(redisTemplate).expire(eq(Constants.LOGIN_FAIL_PREFIX + "testuser"), eq((long) Constants.LOGIN_LOCK_MINUTES), eq(TimeUnit.MINUTES));
    }

    @Test
    @DisplayName("达到最大失败次数 - 触发锁定")
    void shouldLock_whenMaxAttemptsReached() {
        String username = "testuser";
        when(valueOps.increment(Constants.LOGIN_FAIL_PREFIX + username)).thenReturn((long) Constants.LOGIN_MAX_FAIL);
        int count = loginAttemptService.recordFailedAttempt(username);
        assertEquals(Constants.LOGIN_MAX_FAIL, count);
        // 达到阈值时设置锁定 key
        verify(valueOps, times(1)).set(eq(Constants.LOGIN_LOCK_PREFIX + username), eq("1"), eq((long) Constants.LOGIN_LOCK_MINUTES), eq(TimeUnit.MINUTES));
    }

    @Test
    @DisplayName("登录成功 - 清除失败计数")
    void shouldClearAttempts_onSuccess() {
        String username = "testuser";
        loginAttemptService.clearAttempts(username);
        verify(redisTemplate).delete(Constants.LOGIN_FAIL_PREFIX + username);
    }

    @Test
    @DisplayName("无失败记录 - 剩余次数为最大值")
    void shouldReturnMaxRemaining_whenNoFailures() {
        when(valueOps.get(Constants.LOGIN_FAIL_PREFIX + "testuser")).thenReturn(null);
        assertEquals(Constants.LOGIN_MAX_FAIL, loginAttemptService.getRemainingAttempts("testuser"));
    }

    @Test
    @DisplayName("有失败记录 - 计算剩余次数")
    void shouldCalculateRemaining() {
        when(valueOps.get(Constants.LOGIN_FAIL_PREFIX + "testuser")).thenReturn("2");
        assertEquals(Constants.LOGIN_MAX_FAIL - 2, loginAttemptService.getRemainingAttempts("testuser"));
    }
}
