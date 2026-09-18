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

    /** 计算波次的行走距离（按拣货顺序） */
    int calcPathDistance(Long waveId);
}
