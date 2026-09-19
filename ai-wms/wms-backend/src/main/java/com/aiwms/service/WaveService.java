package com.aiwms.service;

import com.aiwms.dto.*;
import com.baomidou.mybatisplus.core.metadata.IPage;

import java.util.List;

public interface WaveService {

    /** 分页查询波次 */
    IPage<WaveVO> pageWaves(WaveQuery query);

    /** 波次详情（含拣货任务） */
    WaveVO getWaveDetail(Long id);

    /**
     * ★ 生成波次：把已分配的订单合并成一批
     * <p>同时按库位聚合生成拣货任务
     */
    WaveVO generateWave(WaveGenerateRequest request);

    /** ★ 拣货确认：allocated → picked（库存总数不变） */
    void pickWave(Long waveId);

    /** ★ 发货确认：真正扣减库存总数 */
    void shipWave(Long waveId);

    /** 查询波次的拣货任务 */
    List<PickingTaskVO> listTasks(Long waveId);

    /**
     * 把优化后的拣货顺序写入 {@code picking_task.seq_no}
     *
     * @param taskIds 按优化顺序排列的任务 ID（必须是该波次的全部任务、不重复）；
     *                传空列表表示清除顺序、恢复到原始顺序
     */
    void applySequence(Long waveId, List<Long> taskIds);

    /** 计算波次的行走距离（按拣货顺序） */
    int calcPathDistance(Long waveId);
}
