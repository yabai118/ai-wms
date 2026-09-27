package com.aiwms.interceptor;

import com.aiwms.common.RequirePermission;
import com.aiwms.common.Result;
import com.aiwms.common.UserContext;
import com.aiwms.service.PermissionService;
import com.aiwms.service.TokenRevocationService;
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

/**
 * 登录鉴权拦截器
 *
 * <p>做三件事，顺序不能换：
 * <pre>
 *   ① 验 token      —— 你是谁          （签名、有效期）
 *   ② 查撤销        —— 这个 token 还算数吗（改密码后旧 token 应立即失效）
 *   ③ 查权限点      —— 你能不能干这个  （表驱动，去 sys_role_permission 查）
 * </pre>
 *
 * <p>白名单不在这里判断，而是由 {@code WebMvcConfig} 的 {@code excludePathPatterns}
 * 排除——那套 pattern 匹配的是<b>去掉 context-path 之后</b>的路径
 * （本项目 context-path 是 {@code /api}，所以写 {@code /auth/login}）。
 *
 * <h3>三个刻意的设计决定</h3>
 *
 * <p><b>① 不抛异常，直接写响应。</b>
 * {@code @RestControllerAdvice} 主要覆盖 handler 执行阶段的异常，
 * 拦截器 preHandle 里抛出来的不一定能被它接住。这里直接置状态码 + 写 JSON。
 *
 * <p><b>② 返回真实的 HTTP 状态码（401/403）。</b>
 * 业务异常（库存不足等）走 HTTP 200 + {@code code} 字段，
 * 但"没登录/没权限"是协议层面的事，用真实状态码更准确，前端也好做统一跳转。
 *
 * <p><b>③ 权限判断委托给 {@link PermissionService}，不在注解上写角色。</b>
 * 注解只声明"这个接口需要什么能力"，"哪个角色有这个能力"是数据库里的一行 ——
 * 所以加角色、调权限都不用改代码。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;
    private final PermissionService permissionService;
    private final TokenRevocationService tokenRevocationService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        // 跨域预检请求不带 token，直接放行
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }

        // ---- ① 验 token ----
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

        // ---- ② 查撤销（改密码后旧 token 立即失效）----
        if (tokenRevocationService.isRevoked(user.staffCode(), user.issuedAt())) {
            log.info("令牌已被撤销，拒绝: user={}", user.staffCode());
            return reject(response, 401, "密码已修改，请重新登录");
        }

        UserContext.set(user);

        // ---- ③ 查权限点 ----
        if (handler instanceof HandlerMethod hm) {
            RequirePermission required = hm.getMethodAnnotation(RequirePermission.class);
            if (required != null && !permissionService.has(user.role(), required.value())) {
                log.warn("越权访问被拦: user={} role={} 需要权限={} api={}",
                        user.staffCode(), user.role(), required.value(), request.getRequestURI());
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

    private boolean reject(HttpServletResponse response, int code, String message) throws Exception {
        response.setStatus(code);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(Result.fail(code, message)));
        return false;
    }
}
