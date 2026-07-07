package com.zmd.order.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置
 *
 * <ul>
 *     <li>分页插件：让 selectPage 真正生效（不加则返回全量数据）</li>
 *     <li>慢 SQL 拦截器：容器启动后由 {@link #registerSlowSqlInterceptor()} 显式注册，
 *     不依赖框架自动收集 bean</li>
 * </ul>
 */
@Slf4j
@Configuration
public class MybatisPlusConfig implements InitializingBean {

    @Value("${slow-sql.threshold-ms:500}")
    private long slowSqlThresholdMs;

    @Autowired
    private SqlSessionFactory sqlSessionFactory;

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        PaginationInnerInterceptor pageInterceptor = new PaginationInnerInterceptor(DbType.MYSQL);
        // 单页最大 500 条，防止恶意大页查询
        pageInterceptor.setMaxLimit(500L);
        interceptor.addInnerInterceptor(pageInterceptor);
        return interceptor;
    }

    @Override
    public void afterPropertiesSet() {
        registerSlowSqlInterceptor();
    }

    /**
     * 显式将慢 SQL 拦截器注册到 MyBatis 拦截器链。
     *
     * <p>不依赖框架自动收集 {@code @Component} 的 Interceptor bean，避免隐式行为；
     * 通过类型判定幂等，防止热重启等场景重复注册。
     */
    public void registerSlowSqlInterceptor() {
        org.apache.ibatis.session.Configuration configuration = sqlSessionFactory.getConfiguration();
        boolean alreadyRegistered = configuration.getInterceptors().stream()
                .anyMatch(i -> i instanceof SlowSqlInterceptor);
        if (!alreadyRegistered) {
            configuration.addInterceptor(new SlowSqlInterceptor(slowSqlThresholdMs));
            log.info("慢SQL拦截器已显式注册, threshold={}ms", slowSqlThresholdMs);
        }
    }
}
