package com.aiwms.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class InboundOrderQuery extends PageQuery {

    /** 入库单号（模糊） */
    private String orderNo;

    /** 状态：0待收货 1待上架 2已完成 */
    private Integer status;

    /** 入库类型 */
    private Integer orderType;
}
