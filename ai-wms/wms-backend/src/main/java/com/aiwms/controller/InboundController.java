package com.aiwms.controller;

import com.aiwms.common.Result;
import com.aiwms.dto.*;
import com.aiwms.service.InboundService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 入库管理接口
 */
@RestController
@RequestMapping("/inbound-orders")
@RequiredArgsConstructor
public class InboundController {

    private final InboundService inboundService;

    /**
     * 分页查询入库单
     * <p>GET /api/inbound-orders?status=0
     */
    @GetMapping
    public Result<IPage<InboundOrderVO>> page(InboundOrderQuery query) {
        return Result.success(inboundService.pageOrders(query));
    }

    /**
     * 入库单详情（含明细）
     * <p>GET /api/inbound-orders/{id}
     */
    @GetMapping("/{id}")
    public Result<InboundOrderVO> detail(@PathVariable Long id) {
        return Result.success(inboundService.getOrderDetail(id));
    }

    /**
     * 创建入库单
     * <p>POST /api/inbound-orders
     */
    @PostMapping
    public Result<Long> create(@RequestBody @Valid InboundCreateRequest request) {
        return Result.success("入库单创建成功", inboundService.createOrder(request));
    }

    /**
     * 收货（填实收数量）
     * <p>POST /api/inbound-orders/{id}/receive
     */
    @PostMapping("/{id}/receive")
    public Result<Void> receive(@PathVariable Long id,
                                @RequestBody @Valid InboundReceiveRequest request) {
        inboundService.receive(id, request);
        return Result.success("收货完成", null);
    }

    /**
     * 上架（指定或推荐货位，库存增加）
     * <p>POST /api/inbound-orders/{id}/shelve
     */
    @PostMapping("/{id}/shelve")
    public Result<Void> shelve(@PathVariable Long id,
                               @RequestBody @Valid InboundShelveRequest request) {
        inboundService.shelve(id, request);
        return Result.success("上架完成，库存已更新", null);
    }

    /**
     * 推荐货位
     * <p>GET /api/inbound-orders/recommend?skuId=1&qty=10&count=3
     */
    @GetMapping("/recommend")
    public Result<List<Long>> recommend(@RequestParam Long skuId,
                                        @RequestParam(defaultValue = "1") Integer qty,
                                        @RequestParam(defaultValue = "3") Integer count) {
        return Result.success(inboundService.recommendLocations(skuId, qty, count));
    }
}
