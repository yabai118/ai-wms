package com.aiwms.controller;

import com.aiwms.common.Result;
import com.aiwms.dto.PickingTaskVO;
import com.aiwms.dto.WaveGenerateRequest;
import com.aiwms.dto.WaveQuery;
import com.aiwms.dto.WaveVO;
import com.aiwms.service.WaveService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 波次与拣货接口
 */
@RestController
@RequestMapping("/waves")
@RequiredArgsConstructor
public class WaveController {

    private final WaveService waveService;

    /**
     * 分页查询波次
     * <p>GET /api/waves
     */
    @GetMapping
    public Result<IPage<WaveVO>> page(WaveQuery query) {
        return Result.success(waveService.pageWaves(query));
    }

    /**
     * 波次详情（含拣货任务）
     * <p>GET /api/waves/{id}
     */
    @GetMapping("/{id}")
    public Result<WaveVO> detail(@PathVariable Long id) {
        return Result.success(waveService.getWaveDetail(id));
    }

    /**
     * ★ 生成波次（把已分配的订单合并成一批）
     * <p>POST /api/waves/generate
     */
    @PostMapping("/generate")
    public Result<WaveVO> generate(@RequestBody @Valid WaveGenerateRequest request) {
        return Result.success("波次生成成功", waveService.generateWave(request));
    }

    /**
     * ★ 拣货确认
     * <p>POST /api/waves/{id}/pick
     */
    @PostMapping("/{id}/pick")
    public Result<Void> pick(@PathVariable Long id) {
        waveService.pickWave(id);
        return Result.success("拣货完成", null);
    }

    /**
     * ★ 发货确认
     * <p>POST /api/waves/{id}/ship
     */
    @PostMapping("/{id}/ship")
    public Result<Void> ship(@PathVariable Long id) {
        waveService.shipWave(id);
        return Result.success("发货完成，库存已扣减", null);
    }

    /**
     * 查询波次的拣货任务
     * <p>GET /api/waves/{id}/tasks
     */
    @GetMapping("/{id}/tasks")
    public Result<List<PickingTaskVO>> tasks(@PathVariable Long id) {
        return Result.success(waveService.listTasks(id));
    }

    /**
     * 计算波次行走距离
     * <p>GET /api/waves/{id}/distance
     */
    @GetMapping("/{id}/distance")
    public Result<Integer> distance(@PathVariable Long id) {
        return Result.success(waveService.calcPathDistance(id));
    }
}
