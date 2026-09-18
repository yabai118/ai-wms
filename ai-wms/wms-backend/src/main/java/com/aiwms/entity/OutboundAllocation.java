package com.aiwms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 出库分配明细（订单视角）★
 *
 * <p>回答的问题：<b>这张订单要的货，从哪个库位取</b>
 *
 * <p>与 picking_task（库位视角）的区别：
 * <ul>
 *   <li>allocation：一行 = 一条订单明细分配到的库位</li>
 *   <li>picking_task：一行 = 拣货员要走的一个库位（可能由多条 allocation 聚合而来）</li>
 * </ul>
 */
@Data
@TableName("outbound_allocation")
public class OutboundAllocation {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderLineId;

    private Long skuId;

    private Long locationId;

    /** 分配数量 */
    private Integer qtyAllocated;

    /** 所属波次（生成波次后填） */
    private Long waveId;

    /** 所属拣货任务（聚合后回填） */
    private Long taskId;

    /** 0已分配 1已拣货 2已释放 */
    private Integer status;

    private LocalDateTime allocatedAt;

    /** 释放时间（超时释放） */
    private LocalDateTime releasedAt;
}
