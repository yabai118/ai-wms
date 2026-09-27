package com.aiwms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 角色
 *
 * <p><b>★ 这张表就是「角色从代码变成数据」的载体。</b>
 * 改造前角色是 {@code Roles.java} 里的四个常量，加一个角色要改代码重新部署；
 * 现在加角色 = 往这张表插一行，服务不用重启。
 *
 * <p>主键是 {@code code}（如 {@code PICKER}）而不是自增 id —— 因为
 * {@code sys_user.role} 里存的就是这个码，用 code 当主键可以**零数据迁移**。
 */
@Data
@TableName("sys_role")
public class SysRole {

    /** 角色码，如 PICKER（与 sys_user.role 的值对应） */
    @TableId(type = IdType.INPUT)
    private String code;

    /** 中文名，如「拣货员」 */
    private String name;

    /** 1=内置角色（不可删除、不可改 code） */
    private Integer builtin;

    /** 0禁用 1启用 */
    private Integer status;

    private String remark;

    private LocalDateTime createdAt;
}
