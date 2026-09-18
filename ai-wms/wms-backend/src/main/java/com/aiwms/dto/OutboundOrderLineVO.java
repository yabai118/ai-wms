package com.aiwms.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class OutboundOrderLineVO {

    private Long id;
    private Long skuId;
    private String skuCode;
    private String reference;
    private BigDecimal sizeUs;
    private Integer qty;

    /** 该明细已分配到的库位（详情接口返回） */
    private List<AllocationVO> allocations;
}
