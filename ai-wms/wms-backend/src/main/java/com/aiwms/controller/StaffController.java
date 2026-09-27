package com.aiwms.controller;

import com.aiwms.common.Permissions;
import com.aiwms.common.RequirePermission;
import com.aiwms.common.Result;
import com.aiwms.dto.StaffOptionVO;
import com.aiwms.dto.StaffQuery;
import com.aiwms.dto.StaffSaveRequest;
import com.aiwms.dto.StaffVO;
import com.aiwms.service.StaffService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 员工档案管理接口
 *
 * <p>补上这个页面之前，员工档案只能靠 SQL 维护 ——
 * 而账号又必须关联员工，等于「定了必须登记的规矩，却没有登记的地方」。
 *
 * <p>权限用 {@code user:manage}（和账号管理同一个）——
 * 在业务上这两件事都是「人事管理」，拆成两个权限点反而要配两遍。
 */
@RestController
@RequestMapping("/staffs")
@RequiredArgsConstructor
public class StaffController {

    private final StaffService staffService;

    /**
     * 分页查询员工（带账号情况）
     * <p>GET /api/staffs?pageNum=1&pageSize=20&staffCode=OP&status=1
     */
    @GetMapping
    @RequirePermission(Permissions.USER_MANAGE)
    public Result<IPage<StaffVO>> page(StaffQuery query) {
        return Result.success(staffService.pageStaff(query));
    }

    /**
     * 新建员工
     * <p>POST /api/staffs
     */
    @PostMapping
    @RequirePermission(Permissions.USER_MANAGE)
    public Result<Long> create(@RequestBody @Valid StaffSaveRequest request) {
        return Result.success("员工创建成功", staffService.createStaff(request));
    }

    /**
     * 编辑员工（只能改姓名，工号是登录名不可改）
     * <p>POST /api/staffs/{id}
     */
    @PostMapping("/{id}")
    @RequirePermission(Permissions.USER_MANAGE)
    public Result<Void> update(@PathVariable Long id,
                               @RequestBody @Valid StaffSaveRequest request) {
        staffService.updateStaff(id, request);
        return Result.success("员工信息已更新", null);
    }

    /**
     * 在职 / 离职
     * <p>POST /api/staffs/{id}/status?status=0|1
     *
     * <p>标记离职时，如果此人有账号，账号会**一并停用**。
     */
    @PostMapping("/{id}/status")
    @RequirePermission(Permissions.USER_MANAGE)
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        staffService.updateStatus(id, status);
        return Result.success(status == 1 ? "已标记为在职" : "已标记为离职，其账号已一并停用", null);
    }

    /**
     * 员工下拉（在职 + 未开账号）
     * <p>GET /api/staffs/options
     *
     * <p>供「新建账号」选人用 —— 离职的和已有账号的都不会出现。
     */
    @GetMapping("/options")
    @RequirePermission(Permissions.USER_MANAGE)
    public Result<List<StaffOptionVO>> options() {
        return Result.success(staffService.listOptions());
    }
}
