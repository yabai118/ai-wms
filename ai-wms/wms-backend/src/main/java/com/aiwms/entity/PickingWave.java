package com.aiwms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 拣货波次
 *
 * <p>波次 = 把多个订单合并成一批，让拣货员一次拣完
 * <p>容量约束：≤ 27 件（拣货车物理容量）
 */
@Data
@TableName("picking_wave")
public class PickingWave {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String waveNo;

    private Long operatorId;

    /** 0待拣货 1拣货中 2已完成 */
    private Integer status;

    /** 载具容量（27 件） */
    private Integer capacity;

    /** 本波次总件数 */
    private Integer totalQty;

    /** 本波次任务数 */
    private Integer totalTasks;

    /** 路径总距离（优化后填） */
    private Integer pathDistance;

    private LocalDateTime createdAt;

    private LocalDateTime finishedAt;
}
