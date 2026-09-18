package com.aiwms.controller;

import com.aiwms.common.Result;
import com.aiwms.dto.InventoryQuery;
import com.aiwms.dto.InventoryTransactionVO;
import com.aiwms.dto.InventoryVO;
import com.aiwms.service.InventoryService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 库存管理接口
 */
@RestController
@RequestMapping("/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;
    private final com.aiwms.service.StockCacheService stockCacheService;

    /**
     * 分页查询库存（五字段）
     * <p>GET /api/inventory?skuCode=8N10W9&onlyAvailable=true
     */
    @GetMapping
    public Result<IPage<InventoryVO>> page(InventoryQuery query) {
        return Result.success(inventoryService.pageInventory(query));
    }

    /**
     * 库存流水（可追溯）
     * <p>GET /api/inventory/transactions
     */
    @GetMapping("/transactions")
    public Result<IPage<InventoryTransactionVO>> transactions(InventoryQuery query) {
        return Result.success(inventoryService.pageTransactions(query));
    }

    /**
     * 库存总览统计
     * <p>GET /api/inventory/summary
     */
    @GetMapping("/summary")
    public Result<Map<String, Object>> summary() {
        return Result.success(inventoryService.summary());
    }

    /**
     * ★ 库存对账（用流水重算验证库存表）
     * <p>GET /api/inventory/reconcile
     */
    @GetMapping("/reconcile")
    public Result<Map<String, Object>> reconcile() {
        return Result.success(inventoryService.reconcile());
    }

    /**
     * ★ 查 SKU 的可用库存总量（走 Redis 缓存）
     * <p>GET /api/inventory/stock/8N10W9-11
     *
     * <p>这是出库前校验的高频读取，用 Cache-Aside 缓存降低数据库压力。
     */
    @GetMapping("/stock/{skuCode}")
    public Result<Map<String, Object>> stock(@PathVariable String skuCode) {
        long t0 = System.currentTimeMillis();
        int available = stockCacheService.getAvailableStock(skuCode);
        long cost = System.currentTimeMillis() - t0;

        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("skuCode", skuCode);
        data.put("available", available);
        data.put("costMs", cost);
        return Result.success(data);
    }

    /**
     * 冻结库存（质检不合格等）
     * <p>POST /api/inventory/{id}/freeze?qty=10&reason=质检不合格
     */
    @PostMapping("/{id}/freeze")
    public Result<Void> freeze(@PathVariable Long id,
                               @RequestParam Integer qty,
                               @RequestParam(required = false) String reason) {
        inventoryService.freeze(id, qty, reason);
        return Result.success("冻结成功", null);
    }

    /**
     * 解冻库存
     * <p>POST /api/inventory/{id}/unfreeze?qty=10
     */
    @PostMapping("/{id}/unfreeze")
    public Result<Void> unfreeze(@PathVariable Long id, @RequestParam Integer qty) {
        inventoryService.unfreeze(id, qty);
        return Result.success("解冻成功", null);
    }
}
