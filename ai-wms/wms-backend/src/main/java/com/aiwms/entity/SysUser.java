package com.aiwms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统账号（登录用）
 *
 * <p><b>★ 与 {@link Operator} 是两张表，故意分开的：</b>
 * <ul>
 *   <li>{@code operator} —— 作业人员档案（谁在仓库里干活），人员/HR 的范畴</li>
 *   <li>{@code sys_user} —— 系统账号（谁登录了系统），系统策略的范畴</li>
 * </ul>
 *
 * <p>两者靠 {@link #operatorId} 关联，可为空。空值的含义是
 * 「这个账号不代表某个具体的现场作业人员」——管理员、主管是管理岗，
 * 本来就不在拣货现场。
 *
 * <p>为什么不合成一张表：这两张表<b>变化的原因不同</b>。人员变动改 operator，
 * 加管理员/改密码/停用账号改 sys_user。而且现实里账号和人不是一一对应的
 * （临时工不登录、由组长代操作），合表就表达不了。
 */
@Data
@TableName("sys_user")
public class SysUser {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 登录名 */
    private String username;

    /** 密码哈希（BCrypt），绝不返回给前端 */
    private String password;

    /** 显示名（顶栏展示用） */
    private String displayName;

    /** 角色：ADMIN / RECEIVER / PICKER / SUPERVISOR */
    private String role;

    /** 关联的作业人员 id（operator.id），管理岗为 null */
    private Long operatorId;

    /** 0禁用 1启用 */
    private Integer status;

    private LocalDateTime lastLoginAt;

    private LocalDateTime createdAt;
}
