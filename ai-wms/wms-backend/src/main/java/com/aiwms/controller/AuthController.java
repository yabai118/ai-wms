package com.aiwms.controller;

import com.aiwms.common.Result;
import com.aiwms.dto.ChangePasswordRequest;
import com.aiwms.dto.LoginRequest;
import com.aiwms.dto.LoginVO;
import com.aiwms.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 登录鉴权接口
 *
 * <p>{@code /auth/login} 在 {@code WebMvcConfig} 的白名单里，不需要登录即可访问；
 * {@code /auth/me} 需要带 token。
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 登录
     * <p>POST /api/auth/login
     */
    @PostMapping("/login")
    public Result<LoginVO> login(@RequestBody @Valid LoginRequest request) {
        return Result.success("登录成功", authService.login(request));
    }

    /**
     * 当前登录用户
     * <p>GET /api/auth/me
     */
    @GetMapping("/me")
    public Result<LoginVO> me() {
        return Result.success(authService.currentUser());
    }

    /**
     * 修改自己的密码
     * <p>POST /api/auth/change-password
     *
     * <p>改的是当前登录的人（用户名从 token 里取，不由请求体传）。
     * 成功后旧令牌立即失效，前端应清凭证回登录页。
     */
    @PostMapping("/change-password")
    public Result<Void> changePassword(@RequestBody @Valid ChangePasswordRequest request) {
        authService.changePassword(request);
        return Result.success("密码修改成功，请重新登录", null);
    }
}
