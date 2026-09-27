package com.aiwms.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口所需权限点
 *
 * <p>标在 Controller 的方法上，由 {@code AuthInterceptor} 读取，
 * 查「当前用户的角色有没有这个权限点」来决定放行还是 403。
 *
 * <pre>
 *   &#64;RequirePermission(Permissions.INBOUND_SHELVE)
 * </pre>
 *
 * <h3>为什么是「权限点」而不是「角色」</h3>
 *
 * <p>这是本项目权限模型的关键决定。如果注解上写角色：
 * <pre>
 *   &#64;RequireRole(Roles.RECEIVER)                    ← 旧写法
 *   &#64;RequirePermission(Permissions.INBOUND_SHELVE)  ← 新写法
 * </pre>
 * 那么「谁能上架」这个答案就被**编译进了 jar 包**——
 * 想让质检员也能上架，得改 Java 代码、重新部署。
 *
 * <p>改成权限点之后，注解只说「这个接口需要『上架』这个能力」，
 * <b>「哪个角色有这个能力」变成数据库里的一行</b>，管理员在界面上勾一下就生效。
 *
 * <h3>默认放行、按需收紧</h3>
 *
 * <p>不标注解 = 登录即可访问（各种 GET 查询）。
 * 这样新增接口不会因为忘了加注解而把所有人挡在外面。
 *
 * <p><b>ADMIN 由拦截器统一放行</b>，不用在每个注解里重复写；
 * 这是刻意保留的后门，防止管理员把权限配错后把自己锁死。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePermission {

    /** 所需权限点，取值见 {@link Permissions} */
    String value();
}
