package com.aiwms.service.impl;

import com.aiwms.common.BusinessException;
import com.aiwms.dto.*;
import com.aiwms.entity.*;
import com.aiwms.mapper.*;
import com.aiwms.service.OutboundService;
import com.aiwms.service.StockCacheService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 出库服务实现
 *
 * <p><b>本类的核心是「并发扣减」——用条件更新防超卖</b>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutboundServiceImpl implements OutboundService {

    private final OutboundOrderMapper orderMapper;
    private final OutboundOrderLineMapper lineMapper;
    private final OutboundAllocationMapper allocationMapper;
    private final InventoryMapper inventoryMapper;
    private final InventoryTransactionMapper transactionMapper;
    private final ProductSkuMapper skuMapper;
    private final ProductMapper productMapper;
    private final CustomerMapper customerMapper;
    private final LocationMapper locationMapper;
    private final StockCacheService stockCacheService;

    private static final Map<Integer, String> ORDER_STATUS = Map.of(
            0, "待分配", 1, "已分配", 2, "拣货中", 3, "已发货", 4, "已取消");
    private static final Map<Integer, String> ALLOC_STATUS = Map.of(
            0, "已分配", 1, "已拣货", 2, "已释放");

    // ==================================================================
    //  查询
    // ==================================================================

    @Override
    public IPage<OutboundOrderVO> pageOrders(OutboundOrderQuery query) {
        LambdaQueryWrapper<OutboundOrder> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getOrderNo())) {
            wrapper.like(OutboundOrder::getOrderNo, query.getOrderNo());
        }
        if (query.getStatus() != null) {
            wrapper.eq(OutboundOrder::getStatus, query.getStatus());
        }
        if (query.getCustomerId() != null) {
            wrapper.eq(OutboundOrder::getCustomerId, query.getCustomerId());
        }
        wrapper.orderByDesc(OutboundOrder::getId);

        Page<OutboundOrder> page = new Page<>(query.getPageNum(), query.getPageSize());
        IPage<OutboundOrder> result = orderMapper.selectPage(page, wrapper);

        List<Long> orderIds = result.getRecords().stream().map(OutboundOrder::getId).toList();
        Map<Long, List<OutboundOrderLine>> lineMap = orderIds.isEmpty() ? Map.of()
                : lineMapper.selectList(new LambdaQueryWrapper<OutboundOrderLine>()
                        .in(OutboundOrderLine::getOrderId, orderIds))
                .stream().collect(Collectors.groupingBy(OutboundOrderLine::getOrderId));

        List<OutboundOrderVO> voList = result.getRecords().stream()
                .map(o -> toVO(o, lineMap.getOrDefault(o.getId(), List.of()), false))
                .toList();

        Page<OutboundOrderVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(voList);
        return voPage;
    }

    @Override
    public OutboundOrderVO getOrderDetail(Long id) {
        OutboundOrder order = orderMapper.selectById(id);
        if (order == null) {
            throw new BusinessException(404, "订单不存在: id=" + id);
        }
        List<OutboundOrderLine> lines = lineMapper.selectList(
                new LambdaQueryWrapper<OutboundOrderLine>()
                        .eq(OutboundOrderLine::getOrderId, id)
                        .orderByAsc(OutboundOrderLine::getId));
        return toVO(order, lines, true);
    }

    @Override
    public List<AllocationVO> listAllocations(Long orderId) {
        List<OutboundOrderLine> lines = lineMapper.selectList(
                new LambdaQueryWrapper<OutboundOrderLine>().eq(OutboundOrderLine::getOrderId, orderId));
        if (lines.isEmpty()) return List.of();

        List<OutboundAllocation> allocs = allocationMapper.selectList(
                new LambdaQueryWrapper<OutboundAllocation>()
                        .in(OutboundAllocation::getOrderLineId,
                                lines.stream().map(OutboundOrderLine::getId).toList())
                        .orderByAsc(OutboundAllocation::getId));
        return buildAllocationVOs(allocs);
    }

    // ==================================================================
    //  ★★ 分配库存（并发扣减核心）
    // ==================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AllocateResultVO allocate(Long orderId) {
        OutboundOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(404, "订单不存在");
        }
        if (order.getStatus() != 0) {
            throw new BusinessException("订单当前状态[" + ORDER_STATUS.get(order.getStatus())
                    + "]，只有「待分配」的订单才能分配库存");
        }

        List<OutboundOrderLine> lines = lineMapper.selectList(
                new LambdaQueryWrapper<OutboundOrderLine>().eq(OutboundOrderLine::getOrderId, orderId));
        if (lines.isEmpty()) {
            throw new BusinessException("订单没有明细，无法分配");
        }

        List<OutboundAllocation> created = new ArrayList<>();
        List<String> shortage = new ArrayList<>();
        int totalQty = 0;

        for (OutboundOrderLine line : lines) {
            int need = line.getQty();                       // 这条明细还需要的数量

            // ① 查该 SKU 有可用库存的库位（按可用量降序，拣货区优先）
            List<Map<String, Object>> locs =
                    inventoryMapper.findAvailableLocations(line.getSkuId());

            for (Map<String, Object> loc : locs) {
                if (need <= 0) break;

                Long locationId = ((Number) loc.get("locationId")).longValue();
                int available = ((Number) loc.get("qtyAvailable")).intValue();
                int alloc = Math.min(need, available);      // 这个库位能分配多少
                if (alloc <= 0) continue;

                // ② ★ 条件更新：只有可用量足够时才扣减成功
                int updated = inventoryMapper.allocateQty(line.getSkuId(), locationId, alloc);
                if (updated == 0) {
                    // 说明并发下被别人抢走了，跳过这个库位，尝试下一个
                    log.warn("库存分配失败（并发竞争）: sku={}, location={}, qty={}",
                            line.getSkuId(), locationId, alloc);
                    continue;
                }

                // ③ 生成分配明细
                OutboundAllocation a = new OutboundAllocation();
                a.setOrderLineId(line.getId());
                a.setSkuId(line.getSkuId());
                a.setLocationId(locationId);
                a.setQtyAllocated(alloc);
                a.setStatus(0);
                allocationMapper.insert(a);
                created.add(a);

                // ④ 写库存流水（溯源）
                InventoryTransaction tx = new InventoryTransaction();
                tx.setSkuId(line.getSkuId());
                tx.setLocationId(locationId);
                tx.setQtyDelta(0);                          // 分配不改总数
                tx.setBizType("ALLOCATE");
                tx.setReferenceType("OUTBOUND_ORDER");
                tx.setReferenceId(orderId);
                tx.setRemark("订单分配: " + order.getOrderNo() + " / 明细 " + line.getId()
                        + " / 分配 " + alloc + " 件");
                tx.setCreatedBy("system");
                transactionMapper.insert(tx);

                need -= alloc;
                totalQty += alloc;
            }

            // ⑤ 如果库位都找完了还不够，说明库存不足
            if (need > 0) {
                ProductSku sku = skuMapper.selectById(line.getSkuId());
                shortage.add(sku == null ? String.valueOf(line.getSkuId()) : sku.getSkuCode());
            }
        }

        // ⑥ 有缺货 → 整个订单分配失败（事务回滚，之前分配的库存全部还原）
        if (!shortage.isEmpty()) {
            throw new BusinessException("库存不足，无法分配。缺货 SKU: " + String.join(", ", shortage));
        }

        // ⑦ 库存可用量变了 → 删缓存（事务提交后执行，避免脏读回填）
        created.stream().map(OutboundAllocation::getSkuId).distinct()
                .forEach(stockCacheService::evictAfterCommit);

        // ⑧ 更新订单状态
        order.setStatus(1);                                 // 已分配
        orderMapper.updateById(order);

        log.info("订单 {} 分配完成：{} 条明细，共 {} 件", order.getOrderNo(), created.size(), totalQty);

        AllocateResultVO vo = new AllocateResultVO();
        vo.setOrderId(orderId);
        vo.setOrderNo(order.getOrderNo());
        vo.setAllocatedLines(created.size());
        vo.setAllocatedQty(totalQty);
        vo.setAllocations(buildAllocationVOs(created));
        vo.setShortageSkus(List.of());
        return vo;
    }

    // ==================================================================
    //  辅助
    // ==================================================================

    private OutboundOrderVO toVO(OutboundOrder o, List<OutboundOrderLine> lines, boolean withLines) {
        OutboundOrderVO vo = new OutboundOrderVO();
        vo.setId(o.getId());
        vo.setOrderNo(o.getOrderNo());
        vo.setCustomerId(o.getCustomerId());
        Customer c = o.getCustomerId() == null ? null : customerMapper.selectById(o.getCustomerId());
        vo.setCustomerCode(c == null ? null : c.getCustCode());
        vo.setStatus(o.getStatus());
        vo.setStatusName(ORDER_STATUS.getOrDefault(o.getStatus(), "未知"));
        vo.setOrderTime(o.getOrderTime());
        vo.setLineCount(lines.size());
        vo.setTotalQty(lines.stream().mapToInt(l -> l.getQty() == null ? 0 : l.getQty()).sum());

        if (withLines) {
            vo.setLines(buildLineVOs(lines));
        }
        return vo;
    }

    private List<OutboundOrderLineVO> buildLineVOs(List<OutboundOrderLine> lines) {
        if (lines.isEmpty()) return List.of();

        // SKU / 商品信息
        Set<Long> skuIds = lines.stream().map(OutboundOrderLine::getSkuId).collect(Collectors.toSet());
        Map<Long, ProductSku> skuMap = skuMapper.selectBatchIds(skuIds).stream()
                .collect(Collectors.toMap(ProductSku::getId, s -> s));
        Map<Long, String> prodMap = skuMap.isEmpty() ? Map.of()
                : productMapper.selectBatchIds(skuMap.values().stream()
                        .map(ProductSku::getProductId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Product::getId, Product::getReference));

        // 分配明细（按明细分组）
        List<Long> lineIds = lines.stream().map(OutboundOrderLine::getId).toList();
        List<OutboundAllocation> allocs = allocationMapper.selectList(
                new LambdaQueryWrapper<OutboundAllocation>()
                        .in(OutboundAllocation::getOrderLineId, lineIds));
        Map<Long, List<AllocationVO>> allocMap = buildAllocationVOs(allocs).stream()
                .collect(Collectors.groupingBy(AllocationVO::getOrderLineId));

        return lines.stream().map(l -> {
            OutboundOrderLineVO vo = new OutboundOrderLineVO();
            vo.setId(l.getId());
            vo.setSkuId(l.getSkuId());
            vo.setQty(l.getQty());
            ProductSku sku = skuMap.get(l.getSkuId());
            if (sku != null) {
                vo.setSkuCode(sku.getSkuCode());
                vo.setSizeUs(sku.getSizeUs());
                vo.setReference(prodMap.get(sku.getProductId()));
            }
            vo.setAllocations(allocMap.getOrDefault(l.getId(), List.of()));
            return vo;
        }).toList();
    }

    private List<AllocationVO> buildAllocationVOs(List<OutboundAllocation> allocs) {
        if (allocs.isEmpty()) return List.of();

        Map<Long, String> locMap = locationMapper.selectBatchIds(
                        allocs.stream().map(OutboundAllocation::getLocationId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Location::getId, Location::getLocationCode));
        Map<Long, String> skuMap = skuMapper.selectBatchIds(
                        allocs.stream().map(OutboundAllocation::getSkuId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(ProductSku::getId, ProductSku::getSkuCode));

        return allocs.stream().map(a -> {
            AllocationVO vo = new AllocationVO();
            vo.setId(a.getId());
            vo.setOrderLineId(a.getOrderLineId());
            vo.setSkuId(a.getSkuId());
            vo.setSkuCode(skuMap.get(a.getSkuId()));
            vo.setLocationId(a.getLocationId());
            vo.setLocationCode(locMap.get(a.getLocationId()));
            vo.setQtyAllocated(a.getQtyAllocated());
            vo.setWaveId(a.getWaveId());
            vo.setTaskId(a.getTaskId());
            vo.setStatus(a.getStatus());
            vo.setStatusName(ALLOC_STATUS.getOrDefault(a.getStatus(), "未知"));
            vo.setAllocatedAt(a.getAllocatedAt());
            return vo;
        }).toList();
    }
}
