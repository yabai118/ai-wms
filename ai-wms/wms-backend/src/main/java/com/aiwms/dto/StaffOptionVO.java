package com.aiwms.dto;

import lombok.Data;

/**
 * 员工下拉选项
 *
 * <p>供新建账号时选择「这个账号对应哪个员工」——
 * 因为账号与员工是一对一的强制关系（{@code sys_user.staff_id} NOT NULL + UNIQUE）。
 */
@Data
public class StaffOptionVO {

    private Long id;

    /** 工号 —— 选了它，登录名就是这个 */
    private String staffCode;

    /** 姓名 —— 选了它，顶栏显示的就是这个 */
    private String staffName;

    /** 是否已被某个账号占用（一对一，已占用的不该再选） */
    private Boolean linked;
}
