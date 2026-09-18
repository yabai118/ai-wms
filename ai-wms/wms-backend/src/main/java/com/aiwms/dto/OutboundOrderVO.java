package com.aiwms.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class OutboundOrderVO {

    private Long id;
    private String orderNo;
    private Long customerId;
    private String customerCode;
    private Integer status;
    private String statusName;
    private LocalDateTime orderTime;

    private Integer lineCount;
    private Integer totalQty;

    /** 明细（详情接口返回） */
    private List<OutboundOrderLineVO> lines;
}
