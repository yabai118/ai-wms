package com.aiwms.controller;

import com.aiwms.common.Result;
import com.aiwms.dto.AllocateResultVO;
import com.aiwms.dto.AllocationVO;
import com.aiwms.dto.OutboundOrderQuery;
import com.aiwms.dto.OutboundOrderVO;
import com.aiwms.service.OutboundService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 出库管理接口
 */
@RestController
@RequestMapping("/outbound-orders")
@RequiredArgsConstructor
public class OutboundController {

    private final OutboundService outboundService;

    /**
     * 分页查询出库单
     * <p>GET /api/outbound-orders?status=0
     */
    @GetMapping
    public Result<IPage<OutboundOrderVO>> page(OutboundOrderQuery query) {
        return Result.success(outboundService.pageOrders(query));
    }

    /**
     * 出库单详情（含明细与分配情况）
     * <p>GET /api/outbound-orders/{id}
     */
    @GetMapping("/{id}")
    public Result<OutboundOrderVO> detail(@PathVariable Long id) {
        return Result.success(outboundService.getOrderDetail(id));
    }

    /**
     * ★ 分配库存
     * <p>POST /api/outbound-orders/{id}/allocate
     */
    @PostMapping("/{id}/allocate")
    public Result<AllocateResultVO> allocate(@PathVariable Long id) {
        AllocateResultVO result = outboundService.allocate(id);
        return Result.success("库存分配成功", result);
    }

    /**
     * 查询某订单的分配明细
     * <p>GET /api/outbound-orders/{id}/allocations
     */
    @GetMapping("/{id}/allocations")
    public Result<List<AllocationVO>> allocations(@PathVariable Long id) {
        return Result.success(outboundService.listAllocations(id));
    }
}
