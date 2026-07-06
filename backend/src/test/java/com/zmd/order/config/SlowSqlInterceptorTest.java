package com.zmd.order.config;

import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.builder.StaticSqlSource;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

@DisplayName("慢SQL拦截器测试")
class SlowSqlInterceptorTest {

    @Test
    @DisplayName("执行SQL调用时不影响原始执行结果")
    void shouldProceedAndReturnOriginalResult() throws Throwable {
        SlowSqlInterceptor interceptor = new SlowSqlInterceptor(0);
        Executor executor = mock(Executor.class);
        MappedStatement ms = new MappedStatement.Builder(
                new Configuration(),
                "test.Mapper.update",
                new StaticSqlSource(new Configuration(), "update work_order set status = ? where id = ?"),
                SqlCommandType.UPDATE)
                .build();

        Method method = Executor.class.getMethod("update", MappedStatement.class, Object.class);
        Invocation invocation = new Invocation(executor, method, new Object[]{ms, null}) {
            @Override
            public Object proceed() {
                return 1;
            }
        };

        assertEquals(1, interceptor.intercept(invocation));
    }

    @Test
    @DisplayName("插件包装返回代理对象")
    void shouldWrapTarget() {
        SlowSqlInterceptor interceptor = new SlowSqlInterceptor(500);
        Executor executor = mock(Executor.class);
        assertNotNull(interceptor.plugin(executor));
    }
}
