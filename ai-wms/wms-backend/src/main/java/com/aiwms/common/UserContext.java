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

    /**
     * 当前登录用户的最小信息集
     *
     * <p>注意带的是 <b>staffCode（工号）</b>而不是"账号名"——
     * 账号名这个概念已经不存在了，登录名就是工号。
     *
     * @param staffId  关联的员工 id（单据记"谁干的"用它）
     * @param staffCode 工号（同时是登录名，写审计流水用它）
     * @param issuedAt  token 的签发时间（epoch 秒）—— 令牌撤销要拿它跟"撤销时间点"比对
     */
    public record CurrentUser(
            Long userId,
            Long staffId,
            String staffCode,
            String staffName,
            String role,
            Long issuedAt) {
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
     * 当前登录人的工号；取不到时返回兜底值。
     *
     * <p>兜底是必要的：导入、定时任务等<b>没有 HTTP 请求上下文</b>的路径
     * 也会写库存流水，那里不应该记成某个人。
     */
    public static String staffCodeOr(String fallback) {
        CurrentUser u = HOLDER.get();
        return u == null || u.staffCode() == null ? fallback : u.staffCode();
    }

    /**
     * 当前登录人关联的员工 id；未登录时为 null
     *
     * <p>用于把业务单据归到"人"头上（如 {@code picking_wave.staff_id}）——
     * <b>从 token 取，不接受前端传参</b>，否则可以冒名。
     */
    public static Long staffId() {
        CurrentUser u = HOLDER.get();
        return u == null ? null : u.staffId();
    }
}
