package com.aiwms.service.impl;

import com.aiwms.common.BusinessException;
import com.aiwms.common.Permissions;
import com.aiwms.dto.SysRoleSaveRequest;
import com.aiwms.dto.SysRoleVO;
import com.aiwms.entity.SysRole;
import com.aiwms.entity.SysRolePermission;
import com.aiwms.entity.SysUser;
import com.aiwms.mapper.SysRoleMapper;
import com.aiwms.mapper.SysRolePermissionMapper;
import com.aiwms.mapper.SysUserMapper;
import com.aiwms.service.PermissionService;
import com.aiwms.service.RoleService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 角色管理实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoleServiceImpl implements RoleService {

    private final SysRoleMapper roleMapper;
    private final SysRolePermissionMapper rolePermissionMapper;
    private final SysUserMapper sysUserMapper;
    private final PermissionService permissionService;

    private static final int STATUS_ENABLED = 1;

    @Override
    public List<SysRoleVO> listRoles() {
        List<SysRole> roles = roleMapper.selectList(
                new LambdaQueryWrapper<SysRole>().orderByDesc(SysRole::getBuiltin)
                        .orderByAsc(SysRole::getCode));
        return roles.stream().map(this::toVO).toList();
    }

    @Override
    public Map<String, String> permissionCatalog() {
        // 直接读常量，不查库——权限点的定义在代码里，库里只有"分配"
        return Permissions.catalog();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createRole(SysRoleSaveRequest request) {
        String code = request.getCode().trim().toUpperCase();
        if (roleMapper.selectById(code) != null) {
            throw new BusinessException("角色码已存在: " + code);
        }

        SysRole role = new SysRole();
        role.setCode(code);
        role.setName(request.getName());
        role.setBuiltin(0);          // 内置角色只能由数据初始化脚本创建
        role.setStatus(STATUS_ENABLED);
        role.setRemark(request.getRemark());
        roleMapper.insert(role);

        savePermissions(code, request.getPermissions());
        log.info("新建角色: {} ({})，权限 {} 个", code, request.getName(),
                request.getPermissions() == null ? 0 : request.getPermissions().size());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePermissions(String code, List<String> permissions) {
        SysRole role = requireRole(code);

        // 先全删再批量插：语义是"全量覆盖"，比逐条 diff 简单且幂等
        rolePermissionMapper.delete(new LambdaQueryWrapper<SysRolePermission>()
                .eq(SysRolePermission::getRoleCode, role.getCode()));
        savePermissions(role.getCode(), permissions);

        // ★ 必须清缓存 —— 否则最多 10 分钟内权限变更不生效
        permissionService.evict(role.getCode());

        log.info("更新角色权限: {} → {} 个权限点", role.getCode(),
                permissions == null ? 0 : permissions.size());
    }

    @Override
    public void updateStatus(String code, Integer status) {
        SysRole role = requireRole(code);
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException("状态值非法，只能是 0（禁用）或 1（启用）");
        }
        if (status == 0 && countUsers(code) > 0) {
            throw new BusinessException("还有 " + countUsers(code)
                    + " 个账号在用这个角色，停用后他们将无法登录。请先调整那些账号的角色");
        }

        SysRole update = new SysRole();
        update.setCode(role.getCode());
        update.setStatus(status);
        roleMapper.updateById(update);
        permissionService.evict(role.getCode());

        log.info("角色 {} 状态改为 {}", role.getCode(), status == 1 ? "启用" : "停用");
    }

    @Override
    public SysRole getByCode(String code) {
        return StringUtils.hasText(code) ? roleMapper.selectById(code) : null;
    }

    // ==================================================================
    //  辅助
    // ==================================================================

    private SysRole requireRole(String code) {
        SysRole role = getByCode(code);
        if (role == null) {
            throw new BusinessException(404, "角色不存在: " + code);
        }
        return role;
    }

    /**
     * 批量保存权限分配
     *
     * <p>会先过滤掉不在 {@link Permissions} 里的非法权限点——
     * 防止有人绕过前端直接调接口塞垃圾数据。用 LinkedHashSet 去重。
     */
    private void savePermissions(String roleCode, List<String> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            return;
        }
        Set<String> valid = new LinkedHashSet<>();
        for (String p : permissions) {
            if (Permissions.isValid(p)) {
                valid.add(p);
            } else {
                log.warn("忽略非法权限点: role={} permission={}", roleCode, p);
            }
        }
        for (String p : valid) {
            SysRolePermission rp = new SysRolePermission();
            rp.setRoleCode(roleCode);
            rp.setPermission(p);
            rolePermissionMapper.insert(rp);
        }
    }

    private int countUsers(String roleCode) {
        return Math.toIntExact(sysUserMapper.selectCount(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getRole, roleCode)));
    }

    private SysRoleVO toVO(SysRole r) {
        SysRoleVO vo = new SysRoleVO();
        vo.setCode(r.getCode());
        vo.setName(r.getName());
        vo.setBuiltin(r.getBuiltin());
        vo.setStatus(r.getStatus());
        vo.setStatusName(r.getStatus() != null && r.getStatus() == STATUS_ENABLED ? "启用" : "停用");
        vo.setRemark(r.getRemark());
        vo.setUserCount(countUsers(r.getCode()));

        // ADMIN 的权限直接展开成全部（与 PermissionService 的口径一致，
        // 因为拦截器对 ADMIN 硬编码放行，库里本来就不存它的权限行）
        if (com.aiwms.common.Roles.ADMIN.equals(r.getCode())) {
            vo.setPermissions(Permissions.all());
        } else {
            List<String> perms = new ArrayList<>(permissionService.permissionsOf(r.getCode()));
            vo.setPermissions(perms);
        }
        return vo;
    }
}
