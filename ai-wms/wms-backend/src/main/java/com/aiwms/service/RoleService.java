package com.aiwms.service;

import com.aiwms.dto.SysRoleSaveRequest;
import com.aiwms.dto.SysRoleVO;
import com.aiwms.entity.SysRole;

import java.util.List;
import java.util.Map;

/**
 * 角色管理
 *
 * <p><b>★ 这个服务就是「角色从代码变成数据」的兑现处。</b>
 * 改造前加一个角色要改 {@code Roles.java} + 重新部署；
 * 现在调这里的 {@link #createRole} 就行，服务不用重启。
 */
public interface RoleService {

    /** 角色列表（含每个角色的权限点和在用账号数） */
    List<SysRoleVO> listRoles();

    /**
     * 权限点清单（code → 中文说明）
     *
     * <p>直接从 {@code Permissions} 常量读，<b>不查库</b>——
     * 权限点的定义天然属于代码，库里只存"分配"。
     */
    Map<String, String> permissionCatalog();

    /** 新建角色 */
    void createRole(SysRoleSaveRequest request);

    /**
     * 修改角色的权限分配（全量覆盖）
     *
     * <p>改完必须让权限缓存失效，否则最多 10 分钟内不生效。
     */
    void updatePermissions(String code, List<String> permissions);

    /** 启用 / 停用角色 */
    void updateStatus(String code, Integer status);

    /**
     * 按 code 查角色（不存在返回 null）
     *
     * <p>供账号管理校验"这个角色码合法吗"用——
     * <b>查表而不是查常量</b>，这正是表驱动的意义。
     */
    SysRole getByCode(String code);
}
