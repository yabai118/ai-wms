package com.aiwms.dto;

import lombok.Data;

import java.util.List;

/**
 * 把路径优化算出的拣货顺序应用到波次
 *
 * <p>算法服务（Python）只读、不写库，算出最优顺序后由前端调本接口落库；
 * 写入的是 {@code picking_task.seq_no}，拣货任务列表本来就按它排序，
 * 所以写完之后拣货单的顺序就变了。
 */
@Data
public class WaveSequenceRequest {

    /**
     * 按优化后的拣货顺序排列的任务 ID 列表。
     *
     * <p>必须是该波次的<b>全部</b>任务，且不能重复。
     * <p>传空列表表示<b>清除</b>优化顺序，恢复到原始顺序。
     */
    private List<Long> taskIds;
}
