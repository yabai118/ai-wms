package com.aiwms.service.impl;

import com.aiwms.common.BusinessException;
import com.aiwms.common.UserContext;
import com.aiwms.dto.ResetPasswordRequest;
import com.aiwms.dto.StaffOptionVO;
import com.aiwms.dto.SysRoleVO;
import com.aiwms.dto.SysUserCreateRequest;
import com.aiwms.dto.SysUserQuery;
import com.aiwms.dto.SysUserVO;
import com.aiwms.entity.Staff;
import com.aiwms.entity.SysRole;
import com.aiwms.entity.SysUser;
import com.aiwms.mapper.StaffMapper;
import com.aiwms.mapper.SysUserMapper;
import com.aiwms.service.RoleService;
import com.aiwms.service.TokenRevocationService;
import com.aiwms.service.UserService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 账号管理实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final SysUserMapper sysUserMapper;
    private final StaffMapper staffMapper;
    private final RoleService roleService;
    private final PasswordEncoder passwordEncoder;
    private final TokenRevocationService tokenRevocationService;

    private static final int STATUS_ENABLED = 1;

    @Override
    public IPage<SysUserVO> pageUsers(SysUserQuery query) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();

        // 工号/姓名不在 sys_user 表上（在 staff 表）——先查 staff 拿 id 列表再过滤，
        // 不做 SQL join（本项目全注解 SQL，不写多表 join）
        if (StringUtils.hasText(query.getStaffCode()) || StringUtils.hasText(query.getStaffName())) {
            LambdaQueryWrapper<Staff> sw = new LambdaQueryWrapper<>();
            if (StringUtils.hasText(query.getStaffCode())) {
                sw.like(Staff::getStaffCode, query.getStaffCode());
            }
            if (StringUtils.hasText(query.getStaffName())) {
                sw.like(Staff::getStaffName, query.getStaffName());
            }
            List<Long> ids = staffMapper.selectList(sw).stream().map(Staff::getId).toList();
            if (ids.isEmpty()) {
                return new Page<>(query.getPageNum(), query.getPageSize(), 0);
            }
            wrapper.in(SysUser::getStaffId, ids);
        }

        if (StringUtils.hasText(query.getRole())) {
            wrapper.eq(SysUser::getRole, query.getRole());
        }
        if (query.getStatus() != null) {
            wrapper.eq(SysUser::getStatus, query.getStatus());
        }
        wrapper.orderByAsc(SysUser::getId);

        Page<SysUser> page = new Page<>(query.getPageNum(), query.getPageSize());
        IPage<SysUser> result = sysUserMapper.selectPage(page, wrapper);

        // 批量加载员工与角色名，避免逐行查询（N+1）
        Map<Long, Staff> staffMap = loadStaffMap(result.getRecords());
        Map<String, String> roleNames = roleService.listRoles().stream()
                .collect(Collectors.toMap(SysRoleVO::getCode, SysRoleVO::getName));

        List<SysUserVO> vos = result.getRecords().stream()
                .map(u -> toVO(u, staffMap.get(u.getStaffId()), roleNames))
                .toList();

        Page<SysUserVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(vos);
        return voPage;
    }

    @Override
    public Long createUser(SysUserCreateRequest request) {
        // ★ 员工必须存在 —— 它决定了登录名和显示名
        Staff staff = staffMapper.selectById(request.getStaffId());
        if (staff == null) {
            throw new BusinessException("员工不存在: id=" + request.getStaffId());
        }

        // ★ 一对一：一个员工只能有一个账号
        Long taken = sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getStaffId, staff.getId()));
        if (taken != null && taken > 0) {
            throw new BusinessException("员工「" + staff.getStaffCode() + " "
                    + staff.getStaffName() + "」已经有账号了");
        }

        // ★ 角色必须存在于 sys_role —— 查表，不是查常量。
        //   这正是"表驱动"的意义：新建的角色立刻能分配，不用改代码
        SysRole role = roleService.getByCode(request.getRole());
        if (role == null) {
            throw new BusinessException("角色不存在: " + request.getRole());
        }
        if (role.getStatus() == null || role.getStatus() != STATUS_ENABLED) {
            throw new BusinessException("角色「" + role.getName() + "」已停用，不能分配给账号");
        }

        SysUser user = new SysUser();
        // 登录名和显示名不在这里设——它们从 staff 表读，不冗余存储
        user.setStaffId(staff.getId());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(role.getCode());
        user.setStatus(STATUS_ENABLED);
        sysUserMapper.insert(user);

        log.info("新建账号: {} {} (角色 {})",
                staff.getStaffCode(), staff.getStaffName(), role.getCode());
        return user.getId();
    }

    @Override
    public void updateStatus(Long id, Integer status) {
        SysUser user = sysUserMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(404, "账号不存在: id=" + id);
        }
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException("状态值非法，只能是 0（停用）或 1（启用）");
        }

        // ★ 不能停用自己 —— 否则管理员一点就把自己锁在门外，只能改数据库
        if (status == 0 && Objects.equals(user.getStaffId(), UserContext.staffId())) {
            throw new BusinessException("不能停用当前登录的账号");
        }

        SysUser update = new SysUser();
        update.setId(user.getId());
        update.setStatus(status);
        sysUserMapper.updateById(update);

        // 停用后旧 token 应当立即失效，否则被停用的人还能继续操作到 token 过期
        if (status == 0) {
            revoke(user);
        }

        log.info("账号 {} 状态改为 {}",
                staffCodeOf(user), status == 1 ? "启用" : "停用");
    }

    @Override
    public void resetPassword(Long id, ResetPasswordRequest request) {
        SysUser user = sysUserMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(404, "账号不存在: id=" + id);
        }

        SysUser update = new SysUser();
        update.setId(user.getId());
        update.setPassword(passwordEncoder.encode(request.getNewPassword()));
        sysUserMapper.updateById(update);

        // 重置密码也要踢下线，否则原密码泄露的情况下重置了也没用
        revoke(user);

        log.info("管理员重置了账号 {} 的密码，旧令牌已撤销", staffCodeOf(user));
    }

    @Override
    public List<StaffOptionVO> listStaff() {
        // 已经被占用的员工（一对一，前端应当禁用这些选项）
        Set<Long> linked = sysUserMapper.selectList(null).stream()
                .map(SysUser::getStaffId).filter(Objects::nonNull)
                .collect(Collectors.toSet());

        return staffMapper.selectList(
                        new LambdaQueryWrapper<Staff>().orderByAsc(Staff::getId))
                .stream().map(s -> {
                    StaffOptionVO vo = new StaffOptionVO();
                    vo.setId(s.getId());
                    vo.setStaffCode(s.getStaffCode());
                    vo.setStaffName(s.getStaffName());
                    vo.setLinked(linked.contains(s.getId()));
                    return vo;
                }).toList();
    }

    // ==================================================================
    //  辅助
    // ==================================================================

    private Map<Long, Staff> loadStaffMap(List<SysUser> users) {
        List<Long> ids = users.stream().map(SysUser::getStaffId)
                .filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return staffMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Staff::getId, Function.identity()));
    }

    /** 撤销某账号的所有令牌（改密码/重置密码/停用后都要调） */
    private void revoke(SysUser user) {
        Staff staff = user.getStaffId() == null ? null : staffMapper.selectById(user.getStaffId());
        if (staff != null) {
            tokenRevocationService.revokeTokensBefore(staff.getStaffCode());
        }
    }

    private String staffCodeOf(SysUser user) {
        Staff staff = user.getStaffId() == null ? null : staffMapper.selectById(user.getStaffId());
        return staff == null ? String.valueOf(user.getId()) : staff.getStaffCode();
    }

    private SysUserVO toVO(SysUser u, Staff staff, Map<String, String> roleNames) {
        SysUserVO vo = new SysUserVO();
        vo.setId(u.getId());
        vo.setStaffId(u.getStaffId());
        vo.setStaffCode(staff == null ? null : staff.getStaffCode());
        vo.setStaffName(staff == null ? null : staff.getStaffName());
        vo.setRole(u.getRole());
        vo.setRoleName(roleNames.getOrDefault(u.getRole(), u.getRole()));
        vo.setStatus(u.getStatus());
        vo.setStatusName(u.getStatus() != null && u.getStatus() == STATUS_ENABLED ? "启用" : "停用");
        vo.setLastLoginAt(u.getLastLoginAt());
        vo.setCreatedAt(u.getCreatedAt());
        return vo;
    }
}
