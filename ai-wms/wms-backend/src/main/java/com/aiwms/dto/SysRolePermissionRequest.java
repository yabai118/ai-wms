package com.aiwms.dto;

import lombok.Data;

import java.util.List;

/**
 * 修改角色权限分配请求
 *
 * <p>语义是「<b>全量覆盖</b>」而不是增量：传进来的列表就是该角色最终应得的全部权限。
 * 实现上是先按 role_code 全删再批量插——比逐条 diff 简单，而且天然幂等。
 */
@Data
public class SysRolePermissionRequest {

    /** 权限点列表，取值见 Permissions 常量 */
    private List<String> permissions;
}
