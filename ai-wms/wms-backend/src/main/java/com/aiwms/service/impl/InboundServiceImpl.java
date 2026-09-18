package com.aiwms.service.impl;

import com.aiwms.common.BusinessException;
import com.aiwms.dto.*;
import com.aiwms.entity.*;
import com.aiwms.mapper.*;
import com.aiwms.service.InboundService;
import com.aiwms.service.StockCacheService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 入库服务实现
 *
 * <p><b>本类的核心是事务（@Transactional）</b>：
 * 上架操作要同时改 3 张表（明细状态 / 库存 / 流水），
 * 必须保证「要么全部成功，要么全部回滚」，否则会出现「库存加了但流水没记」这类脏数据。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InboundServiceImpl implements InboundService {

    private final InboundOrderMapper orderMapper;
    private final InboundOrderLineMapper lineMapper;
    private final ProductSkuMapper skuMapper;
    private final ProductMapper productMapper;
    private final LocationMapper locationMapper;
    private final InventoryMapper inventoryMapper;
    private final InventoryTransactionMapper transactionMapper;
    private final StockCacheService stockCacheService;

    private static final Map<Integer, String> ORDER_STATUS = Map.of(
            0, "待收货", 1, "待上架", 2, "已完成");
    private static final Map<Integer, String> LINE_STATUS = Map.of(
            0, "待收货", 1, "已收货", 2, "已上架");
    private static final Map<Integer, String> ORDER_TYPE = Map.of(
            1, "生产入库", 2, "退货入库", 3, "调拨入库");

    // ==================================================================
    //  查询
    // ==================================================================

    @Override
    public IPage<InboundOrderVO> pageOrders(InboundOrderQuery query) {
        LambdaQueryWrapper<InboundOrder> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getOrderNo())) {
            wrapper.like(InboundOrder::getOrderNo, query.getOrderNo());
        }
        if (query.getStatus() != null) {
            wrapper.eq(InboundOrder::getStatus, query.getStatus());
        }
        if (query.getOrderType() != null) {
            wrapper.eq(InboundOrder::getOrderType, query.getOrderType());
        }
        wrapper.orderByDesc(InboundOrder::getId);

        Page<InboundOrder> page = new Page<>(query.getPageNum(), query.getPageSize());
        IPage<InboundOrder> result = orderMapper.selectPage(page, wrapper);

        // 批量统计明细（避免 N+1）
        List<Long> orderIds = result.getRecords().stream().map(InboundOrder::getId).toList();
        Map<Long, List<InboundOrderLine>> lineMap = orderIds.isEmpty() ? Map.of()
                : lineMapper.selectList(new LambdaQueryWrapper<InboundOrderLine>()
                        .in(InboundOrderLine::getOrderId, orderIds))
                .stream().collect(Collectors.groupingBy(InboundOrderLine::getOrderId));

        List<InboundOrderVO> voList = result.getRecords().stream()
                .map(o -> toVO(o, lineMap.getOrDefault(o.getId(), List.of()), false))
                .toList();

        Page<InboundOrderVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(voList);
        return voPage;
    }

    @Override
    public InboundOrderVO getOrderDetail(Long id) {
        InboundOrder order = orderMapper.selectById(id);
        if (order == null) {
            throw new BusinessException(404, "入库单不存在: id=" + id);
        }
        List<InboundOrderLine> lines = lineMapper.selectList(
                new LambdaQueryWrapper<InboundOrderLine>()
                        .eq(InboundOrderLine::getOrderId, id)
                        .orderByAsc(InboundOrderLine::getId));
        return toVO(order, lines, true);
    }

    // ==================================================================
    //  创建入库单
    // ==================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createOrder(InboundCreateRequest request) {
        // 校验 SKU 是否存在
        for (InboundCreateRequest.Line l : request.getLines()) {
            if (skuMapper.selectById(l.getSkuId()) == null) {
                throw new BusinessException("SKU 不存在: id=" + l.getSkuId());
            }
        }

        InboundOrder order = new InboundOrder();
        order.setOrderNo(generateOrderNo());
        order.setOrderType(request.getOrderType());
        order.setSourceNo(request.getSourceNo());
        order.setStatus(0);                       // 待收货
        order.setExpectedDate(request.getExpectedDate());
        order.setRemark(request.getRemark());
        order.setCreatedBy("admin");
        orderMapper.insert(order);

        for (InboundCreateRequest.Line l : request.getLines()) {
            InboundOrderLine line = new InboundOrderLine();
            line.setOrderId(order.getId());
            line.setSkuId(l.getSkuId());
            line.setPlanQty(l.getPlanQty());
            line.setReceivedQty(0);
            line.setStatus(0);
            lineMapper.insert(line);
        }

        log.info("创建入库单成功: {} ({} 条明细)", order.getOrderNo(), request.getLines().size());
        return order.getId();
    }

    // ==================================================================
    //  收货（填实收数量）
    // ==================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void receive(Long orderId, InboundReceiveRequest request) {
        InboundOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(404, "入库单不存在");
        }
        if (order.getStatus() != 0) {
            throw new BusinessException("当前状态[" + ORDER_STATUS.get(order.getStatus()) + "]不允许收货");
        }

        for (InboundReceiveRequest.Item item : request.getItems()) {
            InboundOrderLine line = lineMapper.selectById(item.getLineId());
            if (line == null || !line.getOrderId().equals(orderId)) {
                throw new BusinessException("明细不存在或不属于该入库单: lineId=" + item.getLineId());
            }
            if (line.getStatus() != 0) {
                throw new BusinessException("明细[" + line.getId() + "]已收货，不能重复收货");
            }
            if (item.getReceivedQty() > line.getPlanQty()) {
                throw new BusinessException("实收数量(" + item.getReceivedQty()
                        + ")不能超过计划数量(" + line.getPlanQty() + ")");
            }

            line.setReceivedQty(item.getReceivedQty());
            line.setStatus(1);                     // 已收货
            lineMapper.updateById(line);
        }

        order.setStatus(1);                        // 待上架
        orderMapper.updateById(order);
        log.info("入库单 {} 收货完成", order.getOrderNo());
    }

    // ==================================================================
    //  上架（★ 事务核心：改 3 张表）
    // ==================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void shelve(Long orderId, InboundShelveRequest request) {
        InboundOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(404, "入库单不存在");
        }
        if (order.getStatus() != 1) {
            throw new BusinessException("当前状态[" + ORDER_STATUS.get(order.getStatus()) + "]不允许上架");
        }

        for (InboundShelveRequest.Item item : request.getItems()) {
            InboundOrderLine line = lineMapper.selectById(item.getLineId());
            if (line == null || !line.getOrderId().equals(orderId)) {
                throw new BusinessException("明细不存在: lineId=" + item.getLineId());
            }
            if (line.getStatus() != 1) {
                throw new BusinessException("明细[" + line.getId() + "]状态不是「已收货」，不能上架");
            }

            // ① 确定货位：传了就用传入的，没传则系统推荐
            Long locationId = item.getLocationId();
            if (locationId == null) {
                List<Long> rec = recommendLocations(line.getSkuId(), line.getReceivedQty(), 1);
                if (rec.isEmpty()) {
                    throw new BusinessException("找不到可用货位，请手动指定");
                }
                locationId = rec.get(0);
            }

            Location location = locationMapper.selectById(locationId);
            if (location == null) {
                throw new BusinessException("货位不存在: id=" + locationId);
            }

            // ② 更新明细：记录货位 + 改状态
            line.setLocationId(locationId);
            line.setStatus(2);                     // 已上架
            lineMapper.updateById(line);

            // ③ 增加库存（不存在则新建）
            int qty = line.getReceivedQty();
            if (qty <= 0) {
                continue;                          // 实收为 0 就不入库存
            }
            int updated = inventoryMapper.increaseQty(line.getSkuId(), locationId, qty);
            if (updated == 0) {
                Inventory inv = new Inventory();
                inv.setSkuId(line.getSkuId());
                inv.setLocationId(locationId);
                inv.setQty(qty);
                inv.setQtyAllocated(0);
                inv.setQtyPicked(0);
                inv.setQtyOnhold(0);
                inv.setQtyAvailable(qty);
                inv.setVersion(0);
                inventoryMapper.insert(inv);
            }

            // ④ 库存变了 → 删掉该 SKU 的缓存（下次读自然回源，保证读到最新值）
            stockCacheService.evictAfterCommit(line.getSkuId());

            // ⑤ 写库存流水（溯源：这条变动是哪张入库单的哪条明细导致的）
            InventoryTransaction tx = new InventoryTransaction();
            tx.setSkuId(line.getSkuId());
            tx.setLocationId(locationId);
            tx.setQtyDelta(qty);
            tx.setBizType("RECEIPT");
            tx.setReferenceType("INBOUND_ORDER");
            tx.setReferenceId(orderId);
            tx.setRemark("入库上架: " + order.getOrderNo() + " / 明细 " + line.getId());
            tx.setCreatedBy("admin");
            transactionMapper.insert(tx);

            // ⑤ 更新货位占用
            location.setUsedSlots(Math.min(location.getCapacity(),
                    (location.getUsedSlots() == null ? 0 : location.getUsedSlots()) + 1));
            location.setStatus(1);                 // 占用
            locationMapper.updateById(location);
        }

        order.setStatus(2);                        // 已完成
        orderMapper.updateById(order);
        log.info("入库单 {} 上架完成，库存已更新", order.getOrderNo());
    }

    // ==================================================================
    //  货位推荐（简化版：找拣货区里占用最少的库位）
    // ==================================================================

    @Override
    public List<Long> recommendLocations(Long skuId, Integer qty, int count) {
        // 简化策略：优先拣货区、按已用商品位数升序
        // 【后续可升级】根据 ABC 分类、商品关联度做智能推荐
        return locationMapper.selectList(new LambdaQueryWrapper<Location>()
                        .eq(Location::getLocationType, 1)       // 拣货区
                        .lt(Location::getUsedSlots, 18)         // 还有空位
                        .orderByAsc(Location::getUsedSlots)
                        .last("LIMIT " + count))
                .stream().map(Location::getId).toList();
    }

    // ==================================================================
    //  辅助方法
    // ==================================================================

    private String generateOrderNo() {
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        long count = orderMapper.selectCount(new LambdaQueryWrapper<InboundOrder>()
                .likeRight(InboundOrder::getOrderNo, "RK" + date)) + 1;
        return String.format("RK%s-%03d", date, count);
    }

    private InboundOrderVO toVO(InboundOrder o, List<InboundOrderLine> lines, boolean withLines) {
        InboundOrderVO vo = new InboundOrderVO();
        vo.setId(o.getId());
        vo.setOrderNo(o.getOrderNo());
        vo.setOrderType(o.getOrderType());
        vo.setOrderTypeName(ORDER_TYPE.getOrDefault(o.getOrderType(), "未知"));
        vo.setSourceNo(o.getSourceNo());
        vo.setStatus(o.getStatus());
        vo.setStatusName(ORDER_STATUS.getOrDefault(o.getStatus(), "未知"));
        vo.setExpectedDate(o.getExpectedDate());
        vo.setRemark(o.getRemark());
        vo.setCreatedBy(o.getCreatedBy());
        vo.setCreatedAt(o.getCreatedAt());
        vo.setLineCount(lines.size());
        vo.setTotalPlanQty(lines.stream().mapToInt(l -> nvl(l.getPlanQty())).sum());
        vo.setTotalReceivedQty(lines.stream().mapToInt(l -> nvl(l.getReceivedQty())).sum());

        if (withLines) {
            vo.setLines(buildLineVOs(lines));
        }
        return vo;
    }

    private List<InboundOrderLineVO> buildLineVOs(List<InboundOrderLine> lines) {
        if (lines.isEmpty()) return List.of();

        Set<Long> skuIds = lines.stream().map(InboundOrderLine::getSkuId).collect(Collectors.toSet());
        Set<Long> locIds = lines.stream().map(InboundOrderLine::getLocationId)
                .filter(Objects::nonNull).collect(Collectors.toSet());

        Map<Long, ProductSku> skuMap = skuMapper.selectBatchIds(skuIds).stream()
                .collect(Collectors.toMap(ProductSku::getId, s -> s));
        Map<Long, String> locMap = locIds.isEmpty() ? Map.of()
                : locationMapper.selectBatchIds(locIds).stream()
                .collect(Collectors.toMap(Location::getId, Location::getLocationCode));
        Set<Long> productIds = skuMap.values().stream().map(ProductSku::getProductId).collect(Collectors.toSet());
        Map<Long, String> prodMap = productIds.isEmpty() ? Map.of()
                : productMapper.selectBatchIds(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Product::getReference));

        return lines.stream().map(l -> {
            InboundOrderLineVO vo = new InboundOrderLineVO();
            vo.setId(l.getId());
            vo.setSkuId(l.getSkuId());
            ProductSku sku = skuMap.get(l.getSkuId());
            if (sku != null) {
                vo.setSkuCode(sku.getSkuCode());
                vo.setSizeUs(sku.getSizeUs());
                vo.setReference(prodMap.get(sku.getProductId()));
            }
            vo.setPlanQty(l.getPlanQty());
            vo.setReceivedQty(l.getReceivedQty());
            vo.setLocationId(l.getLocationId());
            vo.setLocationCode(l.getLocationId() == null ? null : locMap.get(l.getLocationId()));
            vo.setStatus(l.getStatus());
            vo.setStatusName(LINE_STATUS.getOrDefault(l.getStatus(), "未知"));
            return vo;
        }).toList();
    }

    private int nvl(Integer v) {
        return v == null ? 0 : v;
    }
}
