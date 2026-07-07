package com.zmd.order.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置
 *
 * <ul>
 *     <li>分页插件：让 selectPage 真正生效（不加则返回全量数据）</li>
 *     <li>慢 SQL 拦截器：在本配置类中以 {@code @Bean} 显式声明，与分页拦截器并排；
 *     由 MyBatis-Plus 自动配置收集所有 {@code Interceptor} 类型的 bean 挂入
 *     SqlSessionFactory，注册意图明确且无循环依赖。</li>
 * </ul>
 */
@Slf4j
@Configuration
public class MybatisPlusConfig {

    @Value("${slow-sql.threshold-ms:500}")
    private long slowSqlThresholdMs;

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        PaginationInnerInterceptor pageInterceptor = new PaginationInnerInterceptor(DbType.MYSQL);
        // 单页最大 500 条，防止恶意大页查询
        pageInterceptor.setMaxLimit(500L);
        interceptor.addInnerInterceptor(pageInterceptor);
        return interceptor;
    }

    /**
     * 慢 SQL 拦截器（原生 Interceptor，包裹 Executor 计时）。
     *
     * <p>未改用 MyBatis-Plus 的 {@code InnerInterceptor}，因为 3.5.x 的
     * {@code InnerInterceptor} 只有 before* 前置钩子、没有 after 钩子，无法包裹
     * 整个 SQL 执行来计时。慢 SQL 的核心价值就是计时，故保留原生 Interceptor。
     *
     * <p>作为 {@code @Bean} 声明在此处，MyBatis-Plus 自动配置会将其收集进
     * SqlSessionFactory 的插件链（等价于之前的自动收集，但来源更明确）。
     */
    @Bean
    public SlowSqlInterceptor slowSqlInterceptor() {
        return new SlowSqlInterceptor(slowSqlThresholdMs);
    }
}
