package com.aiwms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 出库明细
 */
@Data
@TableName("outbound_order_line")
public class OutboundOrderLine {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;

    private Long skuId;

    /** 订购数量 */
    private Integer qty;

    private LocalDateTime createdAt;
}
