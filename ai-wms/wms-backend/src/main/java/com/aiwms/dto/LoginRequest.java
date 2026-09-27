package com.aiwms.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 登录请求
 *
 * <p>登录名就是<b>工号</b>（{@code staff.staff_code}）——没有单独的"账号名"。
 */
@Data
public class LoginRequest {

    @NotBlank(message = "工号不能为空")
    private String staffCode;

    @NotBlank(message = "密码不能为空")
    private String password;
}
