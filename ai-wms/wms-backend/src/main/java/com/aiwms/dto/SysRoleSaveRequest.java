package com.aiwms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 新建角色请求
 *
 * <p><b>★ 这个 DTO 就是「加角色不用改代码」的入口</b>——
 * 管理员填个码、起个名、勾几个权限点，一个新岗位就上线了。
 */
@Data
public class SysRoleSaveRequest {

    /** 角色码，建议大写字母+下划线，如 INSPECTOR */
    @NotBlank(message = "角色码不能为空")
    @Size(max = 16, message = "角色码不能超过 16 位")
    @Pattern(regexp = "^[A-Z][A-Z0-9_]*$",
            message = "角色码只能用大写字母、数字和下划线，且以字母开头")
    private String code;

    @NotBlank(message = "角色名不能为空")
    @Size(max = 32, message = "角色名不能超过 32 位")
    private String name;

    @Size(max = 128, message = "备注不能超过 128 位")
    private String remark;

    /** 权限点列表，取值见 Permissions 常量；传 null 视为空 */
    private List<String> permissions;
}
