package com.aiwms.controller;

import com.aiwms.common.Permissions;
import com.aiwms.common.RequirePermission;
import com.aiwms.common.Result;
import com.aiwms.dto.SysRolePermissionRequest;
import com.aiwms.dto.SysRoleSaveRequest;
import com.aiwms.dto.SysRoleVO;
import com.aiwms.service.RoleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 角色管理接口
 *
 * <p><b>★ 这组接口就是「角色从代码变成数据」的入口。</b>
 * 管理员在这里建角色、勾权限，新岗位立刻可用，<b>不用改代码、不用重启服务</b>。
 *
 * <p>角色不做物理删除——用停用代替。{@code sys_user.role} 引用了角色码，
 * 删了那些账号就成了孤儿。
 */
@RestController
@RequestMapping("/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    /**
     * 角色列表（含各自的权限点和在用账号数）
     * <p>GET /api/roles
     */
    @GetMapping
    @RequirePermission(Permissions.ROLE_MANAGE)
    public Result<List<SysRoleVO>> list() {
        return Result.success(roleService.listRoles());
    }

    /**
     * 权限点清单
     * <p>GET /api/roles/permissions
     *
     * <p>从 {@code Permissions} 常量读出（不查库），供前端渲染勾选框。
     */
    @GetMapping("/permissions")
    @RequirePermission(Permissions.ROLE_MANAGE)
    public Result<Map<String, String>> permissionCatalog() {
        return Result.success(roleService.permissionCatalog());
    }

    /**
     * 新建角色
     * <p>POST /api/roles
     */
    @PostMapping
    @RequirePermission(Permissions.ROLE_MANAGE)
    public Result<Void> create(@RequestBody @Valid SysRoleSaveRequest request) {
        roleService.createRole(request);
        return Result.success("角色创建成功", null);
    }

    /**
     * 修改角色的权限分配（全量覆盖）
     * <p>POST /api/roles/{code}/permissions
     */
    @PostMapping("/{code}/permissions")
    @RequirePermission(Permissions.ROLE_MANAGE)
    public Result<Void> updatePermissions(@PathVariable String code,
                                          @RequestBody SysRolePermissionRequest request) {
        roleService.updatePermissions(code, request.getPermissions());
        return Result.success("权限已更新，立即生效", null);
    }

    /**
     * 启用 / 停用角色
     * <p>POST /api/roles/{code}/status?status=0|1
     */
    @PostMapping("/{code}/status")
    @RequirePermission(Permissions.ROLE_MANAGE)
    public Result<Void> updateStatus(@PathVariable String code, @RequestParam Integer status) {
        roleService.updateStatus(code, status);
        return Result.success(status == 1 ? "角色已启用" : "角色已停用", null);
    }
}
