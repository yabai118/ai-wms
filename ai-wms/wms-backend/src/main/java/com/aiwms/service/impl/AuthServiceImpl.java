package com.aiwms.service.impl;

import com.aiwms.common.BusinessException;
import com.aiwms.common.Roles;
import com.aiwms.common.UserContext;
import com.aiwms.dto.LoginRequest;
import com.aiwms.dto.LoginVO;
import com.aiwms.entity.SysUser;
import com.aiwms.mapper.SysUserMapper;
import com.aiwms.service.AuthService;
import com.aiwms.util.JwtUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 登录服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    /** 账号启用状态 */
    private static final int STATUS_ENABLED = 1;

    @Override
    public LoginVO login(LoginRequest request) {
        SysUser user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, request.getUsername()));

        // 账号不存在和密码错误返回同一句提示——避免被用来枚举系统里有哪些账号
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            log.warn("登录失败: username={}", request.getUsername());
            throw new BusinessException(401, "用户名或密码错误");
        }

        if (user.getStatus() == null || user.getStatus() != STATUS_ENABLED) {
            throw new BusinessException(403, "账号已停用，请联系管理员");
        }

        // 记录最后登录时间（失败不影响登录本身，所以放在校验之后）
        SysUser touch = new SysUser();
        touch.setId(user.getId());
        touch.setLastLoginAt(LocalDateTime.now());
        sysUserMapper.updateById(touch);

        log.info("登录成功: {} ({})", user.getUsername(), Roles.displayName(user.getRole()));
        return toVO(user, jwtUtil.sign(user));
    }

    @Override
    public LoginVO currentUser() {
        String username = UserContext.usernameOr(null);
        if (username == null) {
            throw new BusinessException(401, "未登录");
        }
        SysUser user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username));
        if (user == null) {
            throw new BusinessException(401, "账号不存在");
        }
        // 不重新签发 token，只回用户信息
        return toVO(user, null);
    }

    private LoginVO toVO(SysUser u, String token) {
        LoginVO vo = new LoginVO();
        vo.setToken(token);
        vo.setUsername(u.getUsername());
        vo.setDisplayName(u.getDisplayName());
        vo.setRole(u.getRole());
        vo.setRoleName(Roles.displayName(u.getRole()));
        vo.setOperatorId(u.getOperatorId());
        return vo;
    }
}
