package com.aiwms.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class OutboundOrderQuery extends PageQuery {

    /** 订单号（模糊） */
    private String orderNo;

    /** 状态：0待分配 1已分配 2拣货中 3已发货 */
    private Integer status;

    /** 客户 ID */
    private Long customerId;
}
