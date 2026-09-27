package com.aiwms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理员重置他人密码请求
 *
 * <p>与 {@link ChangePasswordRequest} 的区别：<b>不需要原密码</b>——
 * 管理员本来就不知道别人的密码。所以这个接口必须严格限权
 * （{@code @RequirePermission(Permissions.USER_MANAGE)}），目前只有 ADMIN 有。
 */
@Data
public class ResetPasswordRequest {

    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 32, message = "密码长度需在 6~32 位之间")
    private String newPassword;
}
