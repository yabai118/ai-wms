package com.aiwms.controller;

import com.aiwms.common.Permissions;
import com.aiwms.common.RequirePermission;
import com.aiwms.common.Result;
import com.aiwms.dto.ResetPasswordRequest;
import com.aiwms.dto.SysUserCreateRequest;
import com.aiwms.dto.SysUserQuery;
import com.aiwms.dto.SysUserVO;
import com.aiwms.service.UserService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 账号管理接口
 *
 * <p>写操作全部标 {@code @RequirePermission(Permissions.USER_MANAGE)}——
 * 默认是"登录即可访问"，不标的话任何登录用户都能建账号、重置别人密码。
 */
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * 分页查询账号
     * <p>GET /api/users?pageNum=1&pageSize=20&staffCode=OP001&role=PICKER&status=1
     */
    @GetMapping
    @RequirePermission(Permissions.USER_MANAGE)
    public Result<IPage<SysUserVO>> page(SysUserQuery query) {
        return Result.success(userService.pageUsers(query));
    }

    /**
     * 新建账号
     * <p>POST /api/users
     */
    @PostMapping
    @RequirePermission(Permissions.USER_MANAGE)
    public Result<Long> create(@RequestBody @Valid SysUserCreateRequest request) {
        return Result.success("账号创建成功", userService.createUser(request));
    }

    /**
     * 启用 / 停用账号
     * <p>POST /api/users/{id}/status?status=0|1
     *
     * <p>不做物理删除——用停用代替。单据引用了操作人，删了审计链就断了。
     */
    @PostMapping("/{id}/status")
    @RequirePermission(Permissions.USER_MANAGE)
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        userService.updateStatus(id, status);
        return Result.success(status == 1 ? "账号已启用" : "账号已停用", null);
    }

    /**
     * 重置他人密码
     * <p>POST /api/users/{id}/reset-password
     *
     * <p>不需要原密码（管理员本来就不知道），所以这个接口权限必须收紧。
     */
    @PostMapping("/{id}/reset-password")
    @RequirePermission(Permissions.USER_MANAGE)
    public Result<Void> resetPassword(@PathVariable Long id,
                                      @RequestBody @Valid ResetPasswordRequest request) {
        userService.resetPassword(id, request);
        return Result.success("密码已重置，该账号的登录状态已失效", null);
    }

    // 员工下拉搬到了 StaffController（GET /api/staffs/options）——
    // 员工档案归员工管理，账号管理只负责「选一个已存在且在职的人」
}
