package com.aiwms.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 员工列表查询参数
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class StaffQuery extends PageQuery {

    /** 工号（模糊）*/
    private String staffCode;

    /** 姓名（模糊）*/
    private String staffName;

    /** 0离职 1在职 */
    private Integer status;
}
