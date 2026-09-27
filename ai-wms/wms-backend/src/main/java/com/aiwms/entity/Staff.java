package com.aiwms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 员工档案
 *
 * <p><b>★ 这张表同时承担两个角色：</b>
 * <ol>
 *   <li><b>登录名与显示名的来源</b> —— {@link #staffCode} 就是登录名，
 *       {@link #staffName} 就是显示名。所以 {@code sys_user} 表里
 *       <b>不再有 username / display_name 两列</b>——那是同一份数据存两遍。</li>
 *   <li><b>作业归属</b> —— {@code picking_wave.staff_id}、{@code inventory_transaction.created_by}
 *       都指向这里，回答"这批货是谁干的"。</li>
 * </ol>
 *
 * <p><b>为什么表里包含管理岗（MG 开头的工号）</b>：
 * {@code sys_user.staff_id} 是 NOT NULL —— 每个账号（含管理员）都必须关联一个员工。
 * 理由是追责：管理员权限最大，出了问题必须查得到是谁在操作。
 * 所以这张表不是"拣货员表"而是"员工档案表"，名字不能叫 operator。
 *
 * <p><b>集成账号（对接 ERP / 物流 / BI）不适用这条规则</b> ——
 * 它们没有工号姓名，将来走 API Key 独立通道，根本不进 {@code sys_user} 表。
 * 详见 {@code 项目设计方案.md} 10.4 节。
 */
@Data
@TableName("staff")
public class Staff {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 工号 —— 同时是登录名 */
    private String staffCode;

    /** 姓名 —— 同时是显示名 */
    private String staffName;

    private LocalDateTime createdAt;
}
