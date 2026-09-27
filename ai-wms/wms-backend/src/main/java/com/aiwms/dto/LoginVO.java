package com.aiwms.dto;

import lombok.Data;

import java.util.List;

/**
 * 登录返回
 *
 * <p><b>⚠️ 绝不包含 password 字段</b>——哪怕哈希也不行。
 */
@Data
public class LoginVO {

    /** JWT，前端存 localStorage，后续请求放 Authorization 头 */
    private String token;

    /** 关联员工 id */
    private Long staffId;

    /** 工号 —— 同时是登录名 */
    private String staffCode;

    /** 姓名 —— 同时是顶栏显示名 */
    private String staffName;

    /** 角色码，如 PICKER */
    private String role;

    /** 角色中文名，如「拣货员」——前端顶栏直接显示 */
    private String roleName;

    /**
     * 该账号拥有的权限点集合 —— <b>前端菜单和按钮据此渲染</b>
     *
     * <p>这是表驱动 RBAC 的关键一步：前端不再写死"哪些角色能看什么"，
     * 而是按后端下发的权限渲染。所以将来加角色时，前端一行都不用改。
     *
     * <p>ADMIN 的这里会被<b>展开成全部权限点</b>（见 {@code PermissionService}），
     * 这样前端不用为超管写特例。
     */
    private List<String> permissions;
}
