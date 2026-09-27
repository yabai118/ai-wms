package com.aiwms.interceptor;

import com.aiwms.common.RequireRole;
import com.aiwms.common.Result;
import com.aiwms.common.Roles;
import com.aiwms.common.UserContext;
import com.aiwms.util.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;

/**
 * 登录鉴权拦截器
 *
 * <p>做两件事：<b>验 token</b>（你是谁）和 <b>查角色</b>（你能不能干这个）。
 *
 * <p>白名单不在这里判断，而是由 {@code WebMvcConfig} 的
 * {@code excludePathPatterns} 排除——那套 pattern 匹配的就是
 * <b>去掉 context-path 之后</b>的路径（本项目 context-path 是 {@code /api}，
 * 所以写 {@code /auth/login} 而不是 {@code /api/auth/login}）。
 *
 * <h3>两个刻意的设计决定</h3>
 *
 * <p><b>① 不抛异常，直接写响应。</b>
 * {@code @RestControllerAdvice} 主要覆盖 handler 执行阶段的异常，
 * 拦截器 preHandle 里抛出来的不一定能被它接住。这里直接置状态码 + 写 JSON，
 * 结果确定可控。
 *
 * <p><b>② 返回真实的 HTTP 状态码（401/403），而不是 HTTP 200 + 业务码。</b>
 * 项目里的业务异常（库存不足等）确实走 HTTP 200 + {@code code} 字段，
 * 但"没登录/没权限"不是业务结果，是协议层面的事，用真实状态码更准确，
 * 前端也能据此做统一跳转。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        // 跨域预检请求不带 token，直接放行
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }

        String token = extractToken(request);
        if (token == null) {
            return reject(response, 401, "未登录，请先登录");
        }

        UserContext.CurrentUser user;
        try {
            user = jwtUtil.parse(token);
        } catch (Exception e) {
            // 过期、签名不对、格式错——对用户来说都是"重新登录一次"
            log.debug("token 校验失败: {}", e.getMessage());
            return reject(response, 401, "登录已过期，请重新登录");
        }

        UserContext.set(user);

        // ---- 角色校验 ----
        if (handler instanceof HandlerMethod hm) {
            RequireRole required = hm.getMethodAnnotation(RequireRole.class);
            if (required != null && !hasRole(user.role(), required.value())) {
                log.warn("越权访问被拦: user={} role={} api={}",
                        user.username(), user.role(), request.getRequestURI());
                return reject(response, 403, "没有权限执行该操作");
            }
        }
        return true;
    }

    /**
     * ⚠️ 必须清理，否则线程复用会把上一个请求的用户带到下一个请求（串号）。
     * 无论请求成功、失败还是抛异常，这里都会执行。
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        UserContext.clear();
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }

    /** ADMIN 统一放行，不用在每个 @RequireRole 里重复写 */
    private boolean hasRole(String userRole, String[] allowed) {
        if (Roles.ADMIN.equals(userRole)) {
            return true;
        }
        return Arrays.asList(allowed).contains(userRole);
    }

    private boolean reject(HttpServletResponse response, int code, String message) throws Exception {
        response.setStatus(code);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(Result.fail(code, message)));
        return false;
    }
}
