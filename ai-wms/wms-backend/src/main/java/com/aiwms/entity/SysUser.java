package com.aiwms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统账号（登录用）
 *
 * <p><b>★ 与 {@link Staff} 是一对一的关系，且这张表只剩"账号"本身的信息：</b>
 *
 * <pre>
 *   staff    —— 员工档案：工号（=登录名）、姓名（=显示名）、作业归属
 *   sys_user —— 系统账号：密码、角色、状态、最后登录时间
 * </pre>
 *
 * <p><b>没有 username / display_name 两列是刻意的</b>——
 * 登录名就是 {@code staff.staff_code}，显示名就是 {@code staff.staff_name}。
 * 单独再存一遍是同一份数据存两遍，改名还得改两处。
 *
 * <p>{@link #staffId} 是 <b>NOT NULL + UNIQUE</b>：
 * <ul>
 *   <li><b>NOT NULL</b> —— 每个账号都必须有人负责，包括管理员。
 *       管理员权限最大，出了问题查不到是谁在操作，审计链就断在最需要它的地方。</li>
 *   <li><b>UNIQUE</b> —— 一个员工只能有一个账号，避免"一个人开好几个号"导致归属混乱。</li>
 * </ul>
 *
 * <p><b>集成账号（ERP / 物流 / BI）不适用</b>——它们没有工号姓名，
 * 将来走 API Key 独立通道，根本不进这张表。见 {@code 项目设计方案.md} 10.4 节。
 */
@Data
@TableName("sys_user")
public class SysUser {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 密码哈希（BCrypt），绝不返回给前端 */
    private String password;

    /** 关联员工（staff.id）—— 登录名与显示名都从这个员工身上取 */
    private Long staffId;

    /** 角色码，取值见 sys_role 表（★ 查表校验，不是硬编码常量） */
    private String role;

    /** 0禁用 1启用 */
    private Integer status;

    private LocalDateTime lastLoginAt;

    private LocalDateTime createdAt;
}
