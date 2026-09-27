package com.aiwms.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 员工返回对象
 *
 * <p>带上了「账号情况」，所以一个页面就能看清：
 * <b>哪些人已经开了账号、哪些还没开</b>（未开账号的直接给个「开账号」按钮）。
 */
@Data
public class StaffVO {

    private Long id;

    /** 工号（= 登录名）*/
    private String staffCode;

    /** 姓名（= 显示名）*/
    private String staffName;

    /** 0离职 1在职 */
    private Integer status;

    private String statusName;

    // ---------- 账号情况 ----------

    /** 是否已开账号 */
    private Boolean hasAccount;

    /** 账号 id（未开账号时为 null，前端用它决定显示「开账号」还是「停用账号」）*/
    private Long userId;

    /** 账号的角色码 */
    private String role;

    /** 角色中文名 */
    private String roleName;

    /** 账号状态：0停用 1启用（未开账号时为 null）*/
    private Integer accountStatus;

    private String accountStatusName;

    private LocalDateTime createdAt;
}
