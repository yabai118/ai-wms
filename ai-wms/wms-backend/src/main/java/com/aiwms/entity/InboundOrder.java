package com.aiwms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 入库单
 *
 * <p>状态流转：0 待收货 → 1 待上架 → 2 已完成
 */
@Data
@TableName("inbound_order")
public class InboundOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 入库单号，如 RK20230105-001 */
    private String orderNo;

    /** 1生产入库 2退货入库 3调拨入库 */
    private Integer orderType;

    /** 来源工单号 */
    private String sourceNo;

    /** 0待收货 1待上架 2已完成 */
    private Integer status;

    private LocalDate expectedDate;

    private String remark;

    private String createdBy;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
