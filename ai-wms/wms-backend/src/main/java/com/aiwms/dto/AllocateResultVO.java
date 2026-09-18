package com.aiwms.dto;

import lombok.Data;

import java.util.List;

/**
 * 分配库存的结果
 */
@Data
public class AllocateResultVO {

    private Long orderId;
    private String orderNo;

    /** 成功分配的明细数与总数量 */
    private Integer allocatedLines;
    private Integer allocatedQty;

    /** 库位分配明细 */
    private List<AllocationVO> allocations;

    /** 库存不足的明细（SKU 编码） */
    private List<String> shortageSkus;
}
