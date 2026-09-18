package com.aiwms.service;

import com.aiwms.dto.InventoryQuery;
import com.aiwms.dto.InventoryTransactionVO;
import com.aiwms.dto.InventoryVO;
import com.baomidou.mybatisplus.core.metadata.IPage;

import java.util.List;
import java.util.Map;

public interface InventoryService {

    /** 分页查询库存（五字段） */
    IPage<InventoryVO> pageInventory(InventoryQuery query);

    /** 库存流水（可按 SKU、库位、业务类型筛选） */
    IPage<InventoryTransactionVO> pageTransactions(InventoryQuery query);

    /** 库存总览统计 */
    Map<String, Object> summary();

    /** 库存对账：用流水累加重算，与库存表比对 */
    Map<String, Object> reconcile();

    /**
     * 冻结库存（质检不合格等场景）
     */
    void freeze(Long inventoryId, Integer qty, String reason);

    /** 解冻 */
    void unfreeze(Long inventoryId, Integer qty);
}
