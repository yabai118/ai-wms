package com.aiwms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 入库明细
 *
 * <p>状态流转：0 待收货 → 1 已收货 → 2 已上架
 */
@Data
@TableName("inbound_order_line")
public class InboundOrderLine {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;

    private Long skuId;

    /** 计划数量 */
    private Integer planQty;

    /** 实收数量（收货时填） */
    private Integer receivedQty;

    /** 上架库位（上架时填） */
    private Long locationId;

    /** 0待收货 1已收货 2已上架 */
    private Integer status;

    private LocalDateTime createdAt;
}
