package com.aiwms.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 账号返回对象
 *
 * <p><b>⚠️ 绝不能直接返回 {@code SysUser} 实体</b>——它带 {@code password} 字段（哪怕只是哈希）。
 * 账号管理是 CRUD，一不小心就会把实体当返回值，这是这个功能最容易出的安全漏洞。
 */
@Data
public class SysUserVO {

    private Long id;

    /** 关联员工 id */
    private Long staffId;

    /** 工号（= 登录名），来自 staff 表 */
    private String staffCode;

    /** 姓名（= 显示名），来自 staff 表 */
    private String staffName;

    /** 角色码 */
    private String role;

    /** 角色中文名（从 sys_role 表读，不是硬编码常量） */
    private String roleName;

    /** 0禁用 1启用 */
    private Integer status;

    /** 状态中文名 */
    private String statusName;

    private LocalDateTime lastLoginAt;

    private LocalDateTime createdAt;
}
