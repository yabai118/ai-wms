package com.aiwms.service;

import com.aiwms.dto.*;
import com.baomidou.mybatisplus.core.metadata.IPage;

import java.util.List;

public interface InboundService {

    /** 分页查询入库单 */
    IPage<InboundOrderVO> pageOrders(InboundOrderQuery query);

    /** 入库单详情（含明细） */
    InboundOrderVO getOrderDetail(Long id);

    /** 创建入库单 */
    Long createOrder(InboundCreateRequest request);

    /** 收货：填实收数量 */
    void receive(Long orderId, InboundReceiveRequest request);

    /** 上架：指定/推荐货位，库存增加 */
    void shelve(Long orderId, InboundShelveRequest request);

    /** 推荐货位（供前端调用） */
    List<Long> recommendLocations(Long skuId, Integer qty, int count);
}
