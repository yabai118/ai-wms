package com.aiwms.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置
 *
 * <p>注册分页插件——没有它，Page 查询不会自动加 LIMIT，会查出全表。
 */
@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // 分页插件（指定 MySQL 方言）
        PaginationInnerInterceptor page = new PaginationInnerInterceptor(DbType.MYSQL);
        page.setMaxLimit(500L);        // 单页最大 500 条，防止一次查太多
        page.setOverflow(false);       // 超过最大页数时返回空，而不是回到第一页
        interceptor.addInnerInterceptor(page);

        return interceptor;
    }
}
