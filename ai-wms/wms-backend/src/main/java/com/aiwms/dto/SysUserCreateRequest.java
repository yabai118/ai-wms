package com.aiwms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新建账号请求
 *
 * <p><b>只有三个字段是必填的：哪个员工 + 初始密码 + 什么角色。</b>
 *
 * <p>没有"登录名"和"显示名"——那两个由员工档案决定：
 * 登录名 = {@code staff.staff_code}（工号），显示名 = {@code staff.staff_name}（姓名）。
 * 让管理员在创建账号时另外起一个名字，等于埋了个"账号名和人名对不上"的坑。
 */
@Data
public class SysUserCreateRequest {

    /** 关联员工（staff.id）—— 决定了登录名与显示名 */
    @NotNull(message = "必须选择一个员工")
    private Long staffId;

    @NotBlank(message = "初始密码不能为空")
    @Size(min = 6, max = 32, message = "密码长度需在 6~32 位之间")
    private String password;

    /** 角色码，必须是 sys_role 里存在的（★ 查表校验，不是查常量） */
    @NotBlank(message = "角色不能为空")
    private String role;
}
