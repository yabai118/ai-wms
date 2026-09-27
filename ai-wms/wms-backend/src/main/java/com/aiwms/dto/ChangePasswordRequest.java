package com.aiwms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改密码请求
 *
 * <p><b>没有 username 字段是刻意的</b>——改的永远是"当前登录的人"，
 * 从 {@code UserContext} 取。让前端传 username 等于开了"改别人密码"的口子。
 *
 * <p>管理员重置别人的密码走另一个接口（{@code POST /users/{id}/reset-password}），
 * 那个不需要原密码，因为管理员本来就不知道。
 */
@Data
public class ChangePasswordRequest {

    @NotBlank(message = "原密码不能为空")
    private String oldPassword;

    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 32, message = "新密码长度需在 6~32 位之间")
    private String newPassword;
}
