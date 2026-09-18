package com.aiwms.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 库存流水返回对象
 */
@Data
public class InventoryTransactionVO {

    private Long id;
    private Long skuId;
    private String skuCode;
    private Long locationId;
    private String locationCode;

    /** 变动量（正负） */
    private Integer qtyDelta;

    /** 业务类型：RECEIPT/PICK/SHIP/ALLOCATE/... */
    private String bizType;

    /** 业务类型中文 */
    private String bizTypeName;

    /** 来源单据类型与 ID（溯源） */
    private String referenceType;
    private Long referenceId;

    private String remark;
    private String createdBy;
    private LocalDateTime createdAt;
}
