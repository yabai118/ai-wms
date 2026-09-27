package com.aiwms.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 账号列表查询参数
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SysUserQuery extends PageQuery {

    /**
     * 工号（模糊）
     *
     * <p>注意这个字段**不在 sys_user 表上**——它在 staff 表。
     * 所以按它筛选时是先查 staff 拿到 id 列表，再用 `staff_id IN (...)` 过滤，
     * 不是 SQL join（本项目全注解 SQL，不做多表 join）。
     */
    private String staffCode;

    /** 姓名（模糊），同上 */
    private String staffName;

    /** 角色码（精确） */
    private String role;

    /** 0禁用 1启用 */
    private Integer status;
}
