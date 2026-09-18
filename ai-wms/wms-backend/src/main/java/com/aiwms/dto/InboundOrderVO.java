package com.aiwms.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 入库单返回对象（表头 + 明细）
 */
@Data
public class InboundOrderVO {

    private Long id;
    private String orderNo;
    private Integer orderType;
    private String orderTypeName;
    private String sourceNo;
    private Integer status;
    private String statusName;
    private LocalDate expectedDate;
    private String remark;
    private String createdBy;
    private LocalDateTime createdAt;

    /** 明细行数 */
    private Integer lineCount;
    /** 计划总数量 */
    private Integer totalPlanQty;
    /** 实收总数量 */
    private Integer totalReceivedQty;

    /** 明细列表（详情接口返回） */
    private List<InboundOrderLineVO> lines;
}
