package com.aiwms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 角色-权限点（哪個角色有哪些权限）
 *
 * <p><b>★ 这张表是「权限分配」，不是「权限定义」。</b>
 * 权限点本身定义在 {@code common/Permissions.java}（因为它对应具体接口），
 * 这里只记录「哪个角色被授予了哪个权限点」。
 *
 * <p>表上是联合主键 {@code (role_code, permission)}。
 * 修改某个角色的权限时，做法是**先按 role_code 全删、再批量插**——
 * 比逐条 diff 简单，而且天然幂等。
 */
@Data
@TableName("sys_role_permission")
public class SysRolePermission {

    /** 这里只是为了满足 MyBatis-Plus 对主键的要求；实际查询都用 LambdaQueryWrapper */
    @TableId(type = IdType.INPUT)
    private String roleCode;

    private String permission;
}
