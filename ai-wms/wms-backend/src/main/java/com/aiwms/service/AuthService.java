package com.aiwms.service;

import com.aiwms.dto.ChangePasswordRequest;
import com.aiwms.dto.LoginRequest;
import com.aiwms.dto.LoginVO;

public interface AuthService {

    /** 登录：校验账号密码，成功则签发 token */
    LoginVO login(LoginRequest request);

    /**
     * 当前登录用户信息
     *
     * <p>前端刷新页面后用它确认 token 还有效（顺带拿到最新的显示名与权限）。
     */
    LoginVO currentUser();

    /**
     * 修改当前登录用户的密码
     *
     * <p>改的是<b>当前登录的人</b>，用户名从 {@code UserContext} 取，不由前端传。
     * 改完会撤销该用户此前签发的所有令牌（旧 token 立即失效）。
     */
    void changePassword(ChangePasswordRequest request);
}
