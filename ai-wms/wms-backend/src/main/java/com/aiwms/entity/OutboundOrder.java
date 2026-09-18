package com.aiwms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 出库单（客户订单）
 *
 * <p>状态流转：0 待分配 → 1 已分配 → 2 拣货中 → 3 已发货
 * <p>数据来源：企业真实订单（32,621 个）
 */
@Data
@TableName("outbound_order")
public class OutboundOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String orderNo;

    private Long customerId;

    /** 0待分配 1已分配 2拣货中 3已发货 4已取消 */
    private Integer status;

    /** 下单时间（真实数据） */
    private LocalDateTime orderTime;

    private LocalDateTime createdAt;
}
