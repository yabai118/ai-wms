package com.aiwms.common;

/**
 * 当前登录用户（ThreadLocal 持有）
 *
 * <p>由 {@code AuthInterceptor} 在请求进入时写入、请求结束时清除，
 * 业务层直接读，不用一路传参。
 *
 * <p><b>⚠️ 必须在 afterCompletion 里 clear()</b> —— Tomcat 的线程是复用的，
 * 不清就会把上一个请求的用户带到下一个请求上（串号），是这类实现最典型的坑。
 */
public final class UserContext {

    private UserContext() {
    }

    /** 当前登录用户的最小信息集 */
    public record CurrentUser(
            Long userId,
            String username,
            String displayName,
            String role,
            Long operatorId) {
    }

    private static final ThreadLocal<CurrentUser> HOLDER = new ThreadLocal<>();

    public static void set(CurrentUser user) {
        HOLDER.set(user);
    }

    /** 取当前用户，未登录时为 null */
    public static CurrentUser get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }

    /**
     * 当前登录名；取不到时返回兜底值。
     *
     * <p>兜底是必要的：导入、定时任务等<b>没有 HTTP 请求上下文</b>的路径
     * 也会写库存流水，那里不应该记成某个人。
     */
    public static String usernameOr(String fallback) {
        CurrentUser u = HOLDER.get();
        return u == null || u.username() == null ? fallback : u.username();
    }

    /** 当前用户关联的作业人员 id；未登录或管理岗为 null */
    public static Long operatorId() {
        CurrentUser u = HOLDER.get();
        return u == null ? null : u.operatorId();
    }
}
