package com.zmd.order.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

import java.util.Properties;

/**
 * MyBatis 慢 SQL 拦截器（原生 Interceptor，包裹 Executor 计时）。
 *
 * <p>超过阈值的 SQL 会输出 WARN 日志，配合 traceId 可定位接口链路。
 *
 * <p>由 {@link MybatisPlusConfig#registerSlowSqlInterceptor()} 在容器启动后
 * <b>显式</b>注册到 MyBatis 拦截器链，不依赖框架自动收集 {@code @Component} 的
 * Interceptor bean，避免隐式行为、意图更明确。
 *
 * <p>注意：未改用 MyBatis-Plus 的 {@code InnerInterceptor}，因为 3.5.x 的
 * {@code InnerInterceptor} 只有 before* 前置钩子、没有 after 钩子，无法包裹
 * 整个 SQL 执行来计时。慢 SQL 的核心价值就是计时，故保留原生 Interceptor。
 */
@Slf4j
@Intercepts({
        @Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class}),
        @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class})
})
public class SlowSqlInterceptor implements Interceptor {

    private final long thresholdMs;

    public SlowSqlInterceptor(long thresholdMs) {
        this.thresholdMs = thresholdMs;
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        long start = System.currentTimeMillis();
        try {
            return invocation.proceed();
        } finally {
            long cost = System.currentTimeMillis() - start;
            if (cost >= thresholdMs) {
                logSlowSql(invocation, cost);
            }
        }
    }

    private void logSlowSql(Invocation invocation, long cost) {
        try {
            Object[] args = invocation.getArgs();
            MappedStatement ms = (MappedStatement) args[0];
            Object parameter = args.length > 1 ? args[1] : null;
            BoundSql boundSql = ms.getBoundSql(parameter);
            String sql = boundSql.getSql().replaceAll("\\s+", " ").trim();
            log.warn("慢SQL: cost={}ms, mapper={}, sql={}", cost, ms.getId(), sql);
        } catch (Exception e) {
            log.warn("慢SQL: cost={}ms, 但SQL解析失败", cost, e);
        }
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {
        // no-op
    }
}
