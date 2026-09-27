package com.aiwms.common;

/**
 * 角色常量
 *
 * <p>四个角色对应系统里已实现的那部分模块，不多不少：
 * <pre>
 *   ADMIN       全部权限（自动放行，不用逐个接口注解）
 *   RECEIVER    收货员 —— 入库、上架
 *   PICKER      拣货员 —— 波次、拣货、路径优化
 *   SUPERVISOR  仓库主管 —— 只读：看板、报表、库存查询
 * </pre>
 *
 * <p>刻意用常量而非枚举：数据库里 role 是 VARCHAR，将来接 RBAC 权限表时
 * 角色会变成数据而非代码，这里只是过渡形态。
 */
public final class Roles {

    private Roles() {
    }

    public static final String ADMIN = "ADMIN";
    public static final String RECEIVER = "RECEIVER";
    public static final String PICKER = "PICKER";
    public static final String SUPERVISOR = "SUPERVISOR";

    /** 角色中文名（返回给前端展示） */
    public static String displayName(String role) {
        if (role == null) {
            return "未知";
        }
        return switch (role) {
            case ADMIN -> "系统管理员";
            case RECEIVER -> "收货员";
            case PICKER -> "拣货员";
            case SUPERVISOR -> "仓库主管";
            default -> "未知";
        };
    }
}
