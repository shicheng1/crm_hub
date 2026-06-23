package com.zmd.order.rate;

import java.lang.annotation.*;

/**
 * 接口限流注解（使用 Redis + Lua 滑动窗口实现）
 *
 * 使用方式：@RateLimit(key = "order:create", maxCount = 10, windowSeconds = 60)
 * 表示：同一用户在 60 秒内最多请求 10 次
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimit {
    /** 限流 key 前缀 */
    String key() default "";
    /** 窗口内最大请求数 */
    int maxCount() default 100;
    /** 窗口大小（秒） */
    int windowSeconds() default 60;
}
