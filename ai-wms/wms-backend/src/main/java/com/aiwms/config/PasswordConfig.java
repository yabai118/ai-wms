package com.aiwms.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 密码编码器
 *
 * <p><b>注意：这不是 Spring Security 的过滤器链。</b>
 * 项目只引了 {@code spring-security-crypto} 这一个模块（纯算法库，
 * 没有 servlet 依赖），所以不会有任何请求被自动接管——鉴权由
 * {@code AuthInterceptor} 自己做。
 *
 * <p>选 BCrypt 而不是 MD5/SHA：它是<b>自带盐、可调计算强度</b>的慢哈希，
 * 专门为抗暴力破解设计。MD5/SHA 是快哈希，拿 GPU 跑彩虹表几秒就能撞出来，
 * 存密码不能用。
 */
@Configuration
public class PasswordConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        // strength 10 ≈ 单次校验 50~100ms，是安全与体验的常用折中
        return new BCryptPasswordEncoder();
    }
}
