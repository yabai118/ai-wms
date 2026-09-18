package com.aiwms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 发货单
 */
@Data
@TableName("shipment")
public class Shipment {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String shipmentNo;

    /** 关联波次 */
    private Long waveId;

    private Integer totalQty;

    /** 0待发货 1已发货 */
    private Integer status;

    private LocalDateTime shippedAt;

    private LocalDateTime createdAt;
}
