package com.aiwms.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口角色限制
 *
 * <p>标在 Controller 的方法上，声明「哪些角色可以调这个接口」。
 * 由 {@code AuthInterceptor} 读取并在放行前校验。
 *
 * <pre>
 *   &#64;RequireRole({Roles.RECEIVER})               // 只有收货员（和 ADMIN）能调
 *   &#64;RequireRole({Roles.RECEIVER, Roles.ADMIN})   // 显式列出管理员也可以
 * </pre>
 *
 * <p><b>设计取向：默认放行、按需收紧。</b>
 * 不标注解 = 登录即可访问（比如各种 GET 查询）。
 * 这样新增接口不会因为忘记加注解而把所有人挡在外面，
 * 而写接口该加注解时一眼就能看出来。
 *
 * <p>ADMIN 由拦截器统一放行，不用在每个注解里重复写。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireRole {

    /** 允许的角色列表，满足其一即可 */
    String[] value();
}
