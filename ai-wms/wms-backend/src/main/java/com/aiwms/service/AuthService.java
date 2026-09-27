package com.aiwms.service;

import com.aiwms.dto.LoginRequest;
import com.aiwms.dto.LoginVO;

public interface AuthService {

    /** 登录：校验账号密码，成功则签发 token */
    LoginVO login(LoginRequest request);

    /**
     * 当前登录用户信息
     *
     * <p>前端刷新页面后用它确认 token 还有效（顺带拿到最新的显示名）。
     */
    LoginVO currentUser();
}
