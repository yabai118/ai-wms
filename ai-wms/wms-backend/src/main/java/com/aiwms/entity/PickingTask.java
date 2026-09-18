package com.aiwms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 拣货任务（库位视角）★
 *
 * <p>回答的问题：<b>拣货员要走哪些库位、各取多少</b>
 *
 * <p>与 outbound_allocation 的关系：
 * 多个 allocation（订单视角）聚合后 → 一个 task（库位视角）
 *
 * <p>数据验证：同一库位在一个波次内平均被访问多次（36.7% 有重复），
 * 聚合后能减少 52% 的行走次数。
 */
@Data
@TableName("picking_task")
public class PickingTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long waveId;

    private Long skuId;

    /** 从哪个库位取 */
    private Long locationId;

    /** 计划取货数量 */
    private Integer qtyPlan;

    /** 实际取货数量 */
    private Integer qtyPicked;

    /** 拣货顺序（路径优化后填） */
    private Integer seqNo;

    /** 0待拣 1已拣 2缺货 */
    private Integer status;

    private LocalDateTime pickedAt;
}
