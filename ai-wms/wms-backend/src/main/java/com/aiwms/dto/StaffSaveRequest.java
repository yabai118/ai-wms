package com.aiwms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新建 / 编辑员工请求
 *
 * <p>注意这里**没有 role 字段** —— 角色属于账号，不属于员工。
 * 「这个人是什么岗位」是账号的事，「这个人是谁」才是档案的事。
 */
@Data
public class StaffSaveRequest {

    /**
     * 工号
     *
     * <p>★ 它同时是**登录名**，所以建成之后不该随便改
     * （改了会让这个人的历史记录对不上、也等于换了用户名）。
     * 编辑时如果传了不同的工号，接口会拒绝。
     */
    @NotBlank(message = "工号不能为空")
    @Size(max = 32, message = "工号不能超过 32 位")
    @Pattern(regexp = "^[A-Za-z0-9_-]+$",
            message = "工号只能用字母、数字、下划线和短横线")
    private String staffCode;

    @NotBlank(message = "姓名不能为空")
    @Size(max = 64, message = "姓名不能超过 64 位")
    private String staffName;
}
