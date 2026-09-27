package com.aiwms.service.impl;

import com.aiwms.common.BusinessException;
import com.aiwms.common.Roles;
import com.aiwms.common.UserContext;
import com.aiwms.dto.ChangePasswordRequest;
import com.aiwms.dto.LoginRequest;
import com.aiwms.dto.LoginVO;
import com.aiwms.entity.Staff;
import com.aiwms.entity.SysUser;
import com.aiwms.mapper.StaffMapper;
import com.aiwms.mapper.SysUserMapper;
import com.aiwms.service.AuthService;
import com.aiwms.service.PermissionService;
import com.aiwms.service.TokenRevocationService;
import com.aiwms.util.JwtUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;

/**
 * 登录服务实现
 *
 * <p><b>登录名的解析链路</b>（账号表里已经没有"账号名"这一列了）：
 * <pre>
 *   用户输入工号 → staff.staff_code 查员工 → sys_user.staff_id 查账号 → 校验密码
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final SysUserMapper sysUserMapper;
    private final StaffMapper staffMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final PermissionService permissionService;
    private final TokenRevocationService tokenRevocationService;

    /** 账号启用状态 */
    private static final int STATUS_ENABLED = 1;

    @Override
    public LoginVO login(LoginRequest request) {
        // ① 按工号找员工（工号就是登录名）
        Staff staff = staffMapper.selectOne(new LambdaQueryWrapper<Staff>()
                .eq(Staff::getStaffCode, request.getStaffCode()));

        // ② 员工没找到、或该员工没有账号、或密码不对 —— 三种情况返回同一句提示，
        //    避免被用来枚举系统里有哪些工号
        SysUser user = staff == null ? null : findByStaffId(staff.getId());
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            log.warn("登录失败: staffCode={}", request.getStaffCode());
            throw new BusinessException(401, "工号或密码错误");
        }

        // ③ 员工信息还查得到（staff 不会为 null，上面已经判过）
        if (user.getStatus() == null || user.getStatus() != STATUS_ENABLED) {
            throw new BusinessException(403, "账号已停用，请联系管理员");
        }

        // 记录最后登录时间（失败不影响登录本身，所以放在校验之后）
        SysUser touch = new SysUser();
        touch.setId(user.getId());
        touch.setLastLoginAt(LocalDateTime.now());
        sysUserMapper.updateById(touch);

        log.info("登录成功: {} {} ({})", staff.getStaffCode(), staff.getStaffName(),
                Roles.displayName(user.getRole()));
        return toVO(user, staff, jwtUtil.sign(user, staff));
    }

    @Override
    public LoginVO currentUser() {
        Long staffId = UserContext.staffId();
        if (staffId == null) {
            throw new BusinessException(401, "未登录");
        }
        Staff staff = staffMapper.selectById(staffId);
        SysUser user = findByStaffId(staffId);
        if (staff == null || user == null) {
            throw new BusinessException(401, "账号不存在");
        }
        // 不重新签发 token，只回用户信息（含最新权限）
        return toVO(user, staff, null);
    }

    @Override
    public void changePassword(ChangePasswordRequest request) {
        // ★ 改的永远是"当前登录的人"——从 UserContext 取，不接受前端传参，
        //   否则就能改别人的密码
        Long staffId = UserContext.staffId();
        if (staffId == null) {
            throw new BusinessException(401, "未登录");
        }
        SysUser user = findByStaffId(staffId);
        if (user == null) {
            throw new BusinessException(401, "账号不存在");
        }

        // 必须校验原密码：否则 token 一旦被盗，攻击者能直接改掉密码把账号锁死
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            log.warn("修改密码失败（原密码错误）: staffId={}", staffId);
            throw new BusinessException(401, "原密码不正确");
        }
        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
            throw new BusinessException("新密码不能与原密码相同");
        }

        // 局部更新：只塞 id + 要改的字段，MyBatis-Plus 不会覆盖其他列
        SysUser update = new SysUser();
        update.setId(user.getId());
        update.setPassword(passwordEncoder.encode(request.getNewPassword()));
        sysUserMapper.updateById(update);

        // ★ 撤销此前签发的所有令牌 —— 不撤销的话旧 token 在过期前照样能用，
        //   "改密码"就只是改了个字符串（JWT 无状态，服务端本来无法作废它）
        tokenRevocationService.revokeTokensBefore(UserContext.staffCodeOr(null));

        log.info("账号 {} 修改了密码，旧令牌已全部撤销", UserContext.staffCodeOr("?"));
    }

    // ==================================================================
    //  辅助
    // ==================================================================

    private SysUser findByStaffId(Long staffId) {
        return sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getStaffId, staffId));
    }

    private LoginVO toVO(SysUser u, Staff staff, String token) {
        LoginVO vo = new LoginVO();
        vo.setToken(token);
        vo.setStaffId(staff.getId());
        vo.setStaffCode(staff.getStaffCode());
        vo.setStaffName(staff.getStaffName());
        vo.setRole(u.getRole());
        vo.setRoleName(Roles.displayName(u.getRole()));
        // ADMIN 会在这里被展开成全部权限点，前端无需为超管写特例
        vo.setPermissions(new ArrayList<>(permissionService.permissionsOf(u.getRole())));
        return vo;
    }
}
