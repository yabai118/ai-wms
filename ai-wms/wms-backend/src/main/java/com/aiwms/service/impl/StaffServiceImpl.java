package com.aiwms.service.impl;

import com.aiwms.common.BusinessException;
import com.aiwms.common.UserContext;
import com.aiwms.dto.StaffOptionVO;
import com.aiwms.dto.StaffQuery;
import com.aiwms.dto.StaffSaveRequest;
import com.aiwms.dto.StaffVO;
import com.aiwms.dto.SysRoleVO;
import com.aiwms.entity.Staff;
import com.aiwms.entity.SysUser;
import com.aiwms.mapper.StaffMapper;
import com.aiwms.mapper.SysUserMapper;
import com.aiwms.service.RoleService;
import com.aiwms.service.StaffService;
import com.aiwms.service.TokenRevocationService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 员工档案实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StaffServiceImpl implements StaffService {

    private final StaffMapper staffMapper;
    private final SysUserMapper sysUserMapper;
    private final RoleService roleService;
    private final TokenRevocationService tokenRevocationService;

    private static final int STATUS_ACTIVE = 1;

    @Override
    public IPage<StaffVO> pageStaff(StaffQuery query) {
        LambdaQueryWrapper<Staff> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getStaffCode())) {
            wrapper.like(Staff::getStaffCode, query.getStaffCode());
        }
        if (StringUtils.hasText(query.getStaffName())) {
            wrapper.like(Staff::getStaffName, query.getStaffName());
        }
        if (query.getStatus() != null) {
            wrapper.eq(Staff::getStatus, query.getStatus());
        }
        wrapper.orderByAsc(Staff::getId);

        Page<Staff> page = new Page<>(query.getPageNum(), query.getPageSize());
        IPage<Staff> result = staffMapper.selectPage(page, wrapper);

        // 批量加载账号信息（避免逐行查询），并把角色码翻成中文
        List<Long> staffIds = result.getRecords().stream().map(Staff::getId).toList();
        Map<Long, SysUser> userMap = staffIds.isEmpty() ? Map.of()
                : sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>()
                        .in(SysUser::getStaffId, staffIds))
                .stream().collect(Collectors.toMap(SysUser::getStaffId, u -> u));
        Map<String, String> roleNames = roleService.listRoles().stream()
                .collect(Collectors.toMap(SysRoleVO::getCode, SysRoleVO::getName));

        List<StaffVO> vos = result.getRecords().stream()
                .map(s -> toVO(s, userMap.get(s.getId()), roleNames))
                .toList();

        Page<StaffVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(vos);
        return voPage;
    }

    @Override
    public Long createStaff(StaffSaveRequest request) {
        String code = request.getStaffCode().trim();

        Long exists = staffMapper.selectCount(new LambdaQueryWrapper<Staff>()
                .eq(Staff::getStaffCode, code));
        if (exists != null && exists > 0) {
            throw new BusinessException("工号已存在: " + code);
        }

        Staff staff = new Staff();
        staff.setStaffCode(code);
        staff.setStaffName(request.getStaffName().trim());
        staff.setStatus(STATUS_ACTIVE);
        staffMapper.insert(staff);

        log.info("新建员工: {} {}", code, staff.getStaffName());
        return staff.getId();
    }

    @Override
    public void updateStaff(Long id, StaffSaveRequest request) {
        Staff staff = requireStaff(id);

        // ★ 工号是登录名，不允许改 —— 改了等于换用户名，且历史记录会对不上
        String newCode = request.getStaffCode().trim();
        if (!staff.getStaffCode().equals(newCode)) {
            throw new BusinessException("工号是登录名，不能修改。如需变更请停用后新建");
        }

        Staff update = new Staff();
        update.setId(staff.getId());
        update.setStaffName(request.getStaffName().trim());
        staffMapper.updateById(update);

        log.info("员工 {} 姓名改为 {}", staff.getStaffCode(), update.getStaffName());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, Integer status) {
        Staff staff = requireStaff(id);
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException("状态值非法，只能是 0（离职）或 1（在职）");
        }

        // 不能把自己标记离职 —— 和账号那边同样的理由：别把自己锁在门外
        if (status == 0 && Objects.equals(staff.getId(), UserContext.staffId())) {
            throw new BusinessException("不能把当前登录的人标记为离职");
        }

        Staff update = new Staff();
        update.setId(staff.getId());
        update.setStatus(status);
        staffMapper.updateById(update);

        // ★ 离职时连带停用账号 —— 一次操作表达一个业务事实，
        //   不该让人先停账号再标离职、还可能漏掉一步
        if (status == 0) {
            SysUser user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getStaffId, staff.getId()));
            if (user != null && user.getStatus() != null && user.getStatus() == STATUS_ACTIVE) {
                SysUser u = new SysUser();
                u.setId(user.getId());
                u.setStatus(0);
                sysUserMapper.updateById(u);

                tokenRevocationService.revokeTokensBefore(staff.getStaffCode());
                log.info("员工 {} 离职，其账号已连带停用、令牌已撤销", staff.getStaffCode());
            }
        }

        log.info("员工 {} 状态改为 {}", staff.getStaffCode(), status == 1 ? "在职" : "离职");
    }

    @Override
    public List<StaffOptionVO> listOptions() {
        // 已开账号的员工 id
        var linked = sysUserMapper.selectList(null).stream()
                .map(SysUser::getStaffId).filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // ★ 只给「在职 + 未开账号」的人 —— 离职的不该再出现在这里
        return staffMapper.selectList(new LambdaQueryWrapper<Staff>()
                        .eq(Staff::getStatus, STATUS_ACTIVE)
                        .orderByAsc(Staff::getId))
                .stream()
                .filter(s -> !linked.contains(s.getId()))
                .map(s -> {
                    StaffOptionVO vo = new StaffOptionVO();
                    vo.setId(s.getId());
                    vo.setStaffCode(s.getStaffCode());
                    vo.setStaffName(s.getStaffName());
                    vo.setLinked(false);
                    return vo;
                }).toList();
    }

    // ==================================================================
    //  辅助
    // ==================================================================

    private Staff requireStaff(Long id) {
        Staff staff = staffMapper.selectById(id);
        if (staff == null) {
            throw new BusinessException(404, "员工不存在: id=" + id);
        }
        return staff;
    }

    private StaffVO toVO(Staff s, SysUser u, Map<String, String> roleNames) {
        StaffVO vo = new StaffVO();
        vo.setId(s.getId());
        vo.setStaffCode(s.getStaffCode());
        vo.setStaffName(s.getStaffName());
        vo.setStatus(s.getStatus());
        vo.setStatusName(s.getStatus() != null && s.getStatus() == STATUS_ACTIVE ? "在职" : "离职");
        vo.setCreatedAt(s.getCreatedAt());

        vo.setHasAccount(u != null);
        if (u != null) {
            vo.setUserId(u.getId());
            vo.setRole(u.getRole());
            vo.setRoleName(roleNames.getOrDefault(u.getRole(), u.getRole()));
            vo.setAccountStatus(u.getStatus());
            vo.setAccountStatusName(
                    u.getStatus() != null && u.getStatus() == 1 ? "启用" : "停用");
        }
        return vo;
    }
}
