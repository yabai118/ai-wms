package com.aiwms.service;

import com.aiwms.dto.AllocateResultVO;
import com.aiwms.dto.AllocationVO;
import com.aiwms.dto.OutboundOrderQuery;
import com.aiwms.dto.OutboundOrderVO;
import com.baomidou.mybatisplus.core.metadata.IPage;

import java.util.List;

public interface OutboundService {

    /** 分页查询出库单 */
    IPage<OutboundOrderVO> pageOrders(OutboundOrderQuery query);

    /** 出库单详情（含明细与分配情况） */
    OutboundOrderVO getOrderDetail(Long id);

    /**
     * ★ 分配库存：为订单的每条明细分配具体库位
     *
     * <p>这是并发扣减的核心：用条件更新防超卖。
     */
    AllocateResultVO allocate(Long orderId);

    /** 查询某订单的分配明细 */
    List<AllocationVO> listAllocations(Long orderId);
}
