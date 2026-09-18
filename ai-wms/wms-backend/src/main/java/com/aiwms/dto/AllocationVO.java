package com.aiwms.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 分配明细返回对象
 */
@Data
public class AllocationVO {

    private Long id;
    private Long orderLineId;
    private Long skuId;
    private String skuCode;
    private Long locationId;
    private String locationCode;
    private Integer qtyAllocated;
    private Long waveId;
    private Long taskId;
    private Integer status;
    private String statusName;
    private LocalDateTime allocatedAt;
}
