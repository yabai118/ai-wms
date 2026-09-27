package com.aiwms.dto;

import lombok.Data;

import java.util.List;

/**
 * 角色返回对象
 */
@Data
public class SysRoleVO {

    private String code;

    private String name;

    /** 1=内置角色（不可删除、不可改 code） */
    private Integer builtin;

    /** 0禁用 1启用 */
    private Integer status;

    private String statusName;

    private String remark;

    /** 该角色拥有的权限点 */
    private List<String> permissions;

    /** 有多少账号在用这个角色 —— 停用/删除前要看它 */
    private Integer userCount;
}
