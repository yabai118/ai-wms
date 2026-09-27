package com.aiwms.config;

import com.aiwms.interceptor.AuthInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置
 *
 * <p>目前只做一件事：注册登录鉴权拦截器。
 *
 * <p><b>⚠️ 白名单路径不写 context-path。</b>
 * 本项目 {@code server.servlet.context-path=/api}，而拦截器的
 * {@code addPathPatterns} / {@code excludePathPatterns} 匹配的是
 * <b>剥掉 context-path 之后</b>的路径——所以写 {@code /auth/login}，
 * 写成 {@code /api/auth/login} 会永远匹配不上，登录接口反而被拦死。
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/auth/login",   // 登录接口本身当然不能要求先登录
                        "/health",       // 健康检查（启动脚本、探活用）
                        "/error"         // Spring 的错误转发端点
                );
    }
}
