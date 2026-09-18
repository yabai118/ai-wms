package com.aiwms.service.impl;

import com.aiwms.common.BusinessException;
import com.aiwms.dto.InventoryQuery;
import com.aiwms.dto.InventoryTransactionVO;
import com.aiwms.dto.InventoryVO;
import com.aiwms.entity.*;
import com.aiwms.mapper.*;
import com.aiwms.service.InventoryService;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryServiceImpl implements InventoryService {

    private final InventoryMapper inventoryMapper;
    private final InventoryTransactionMapper transactionMapper;
    private final ProductSkuMapper skuMapper;
    private final ProductMapper productMapper;
    private final LocationMapper locationMapper;
    private final WarehouseAreaMapper areaMapper;

    private static final Map<String, String> BIZ_TYPE_NAMES = Map.of(
            "RECEIPT", "入库收货",
            "ALLOCATE", "订单分配",
            "PICK", "拣货确认",
            "SHIP", "发货出库",
            "FREEZE", "冻结",
            "RELEASE", "解冻/释放",
            "ADJUST", "盘点调整");

    // ==================================================================
    //  库存查询
    // ==================================================================

    @Override
    public IPage<InventoryVO> pageInventory(InventoryQuery query) {
        // ① 如果按 SKU 编码/款号筛选，先查出匹配的 skuId 集合
        Set<Long> skuFilter = null;
        if (StringUtils.hasText(query.getSkuCode()) || StringUtils.hasText(query.getReference())) {
            LambdaQueryWrapper<ProductSku> sw = new LambdaQueryWrapper<>();
            if (StringUtils.hasText(query.getSkuCode())) {
                sw.like(ProductSku::getSkuCode, query.getSkuCode());
            }
            if (StringUtils.hasText(query.getReference())) {
                Set<Long> pids = productMapper.selectList(new LambdaQueryWrapper<Product>()
                                .like(Product::getReference, query.getReference()))
                        .stream().map(Product::getId).collect(Collectors.toSet());
                if (pids.isEmpty()) {
                    return emptyPage(query);
                }
                sw.in(ProductSku::getProductId, pids);
            }
            skuFilter = skuMapper.selectList(sw).stream()
                    .map(ProductSku::getId).collect(Collectors.toSet());
            if (skuFilter.isEmpty()) {
                return emptyPage(query);
            }
        }

        // ② 库位筛选
        Set<Long> locFilter = null;
        if (StringUtils.hasText(query.getLocationCode())) {
            locFilter = locationMapper.selectList(new LambdaQueryWrapper<Location>()
                            .like(Location::getLocationCode, query.getLocationCode()))
                    .stream().map(Location::getId).collect(Collectors.toSet());
            if (locFilter.isEmpty()) {
                return emptyPage(query);
            }
        }

        // ③ 查库存
        LambdaQueryWrapper<Inventory> wrapper = new LambdaQueryWrapper<>();
        if (skuFilter != null) wrapper.in(Inventory::getSkuId, skuFilter);
        if (locFilter != null) wrapper.in(Inventory::getLocationId, locFilter);
        if (Boolean.TRUE.equals(query.getOnlyAvailable())) wrapper.gt(Inventory::getQtyAvailable, 0);
        if (Boolean.TRUE.equals(query.getOnlyAllocated())) wrapper.gt(Inventory::getQtyAllocated, 0);
        wrapper.orderByDesc(Inventory::getQty).orderByAsc(Inventory::getId);

        Page<Inventory> page = new Page<>(query.getPageNum(), query.getPageSize());
        IPage<Inventory> result = inventoryMapper.selectPage(page, wrapper);

        // ④ 批量加载关联信息（避免 N+1）
        Map<Long, ProductSku> skuMap = loadSkus(result.getRecords());
        Map<Long, String> prodMap = loadProductRefs(skuMap.values());
        Map<Long, Location> locMap = loadLocations(result.getRecords());
        Map<Long, String> areaMap = areaMapper.selectList(null).stream()
                .collect(Collectors.toMap(WarehouseArea::getId, WarehouseArea::getAreaCode));

        List<InventoryVO> voList = result.getRecords().stream()
                .map(i -> toVO(i, skuMap, prodMap, locMap, areaMap))
                .toList();

        Page<InventoryVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(voList);
        return voPage;
    }

    // ==================================================================
    //  库存流水
    // ==================================================================

    @Override
    public IPage<InventoryTransactionVO> pageTransactions(InventoryQuery query) {
        LambdaQueryWrapper<InventoryTransaction> wrapper = new LambdaQueryWrapper<>();

        if (StringUtils.hasText(query.getSkuCode())) {
            Set<Long> ids = skuMapper.selectList(new LambdaQueryWrapper<ProductSku>()
                            .like(ProductSku::getSkuCode, query.getSkuCode()))
                    .stream().map(ProductSku::getId).collect(Collectors.toSet());
            if (ids.isEmpty()) {
                return new Page<>(query.getPageNum(), query.getPageSize(), 0);
            }
            wrapper.in(InventoryTransaction::getSkuId, ids);
        }
        if (StringUtils.hasText(query.getLocationCode())) {
            Set<Long> ids = locationMapper.selectList(new LambdaQueryWrapper<Location>()
                            .like(Location::getLocationCode, query.getLocationCode()))
                    .stream().map(Location::getId).collect(Collectors.toSet());
            if (ids.isEmpty()) {
                return new Page<>(query.getPageNum(), query.getPageSize(), 0);
            }
            wrapper.in(InventoryTransaction::getLocationId, ids);
        }
        wrapper.orderByDesc(InventoryTransaction::getId);

        Page<InventoryTransaction> page = new Page<>(query.getPageNum(), query.getPageSize());
        IPage<InventoryTransaction> result = transactionMapper.selectPage(page, wrapper);

        Map<Long, String> skuMap = skuMapper.selectBatchIds(
                        result.getRecords().stream().map(InventoryTransaction::getSkuId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(ProductSku::getId, ProductSku::getSkuCode));
        Map<Long, String> locMap = locationMapper.selectBatchIds(
                        result.getRecords().stream().map(InventoryTransaction::getLocationId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Location::getId, Location::getLocationCode));

        List<InventoryTransactionVO> voList = result.getRecords().stream().map(t -> {
            InventoryTransactionVO vo = new InventoryTransactionVO();
            vo.setId(t.getId());
            vo.setSkuId(t.getSkuId());
            vo.setSkuCode(skuMap.get(t.getSkuId()));
            vo.setLocationId(t.getLocationId());
            vo.setLocationCode(locMap.get(t.getLocationId()));
            vo.setQtyDelta(t.getQtyDelta());
            vo.setBizType(t.getBizType());
            vo.setBizTypeName(BIZ_TYPE_NAMES.getOrDefault(t.getBizType(), t.getBizType()));
            vo.setReferenceType(t.getReferenceType());
            vo.setReferenceId(t.getReferenceId());
            vo.setRemark(t.getRemark());
            vo.setCreatedBy(t.getCreatedBy());
            vo.setCreatedAt(t.getCreatedAt());
            return vo;
        }).toList();

        Page<InventoryTransactionVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(voList);
        return voPage;
    }

    // ==================================================================
    //  统计与对账
    // ==================================================================

    @Override
    public Map<String, Object> summary() {
        Map<String, Object> map = new LinkedHashMap<>(inventoryMapper.summary());
        map.put("bizTypes", inventoryMapper.countByBizType());
        return map;
    }

    /**
     * 库存对账：用流水累加重算库存，与库存表比对
     *
     * <p>原理：inventory.qty 应该等于该 (sku, location) 所有流水的 qty_delta 之和。
     * <p>这是排查库存问题的核心手段——如果对不上，说明有操作没记流水。
     */
    @Override
    public Map<String, Object> reconcile() {
        List<Map<String, Object>> mismatches = inventoryMapper.reconcile();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mismatchCount", mismatches.size());
        result.put("mismatches", mismatches);
        result.put("consistent", mismatches.isEmpty());
        result.put("message", mismatches.isEmpty()
                ? "库存与流水完全一致"
                : "发现 " + mismatches.size() + " 条不一致记录");
        return result;
    }

    // ==================================================================
    //  冻结 / 解冻
    // ==================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void freeze(Long inventoryId, Integer qty, String reason) {
        if (qty == null || qty <= 0) {
            throw new BusinessException("冻结数量必须大于 0");
        }
        Inventory inv = inventoryMapper.selectById(inventoryId);
        if (inv == null) {
            throw new BusinessException(404, "库存记录不存在");
        }

        int updated = inventoryMapper.freezeQty(inventoryId, qty);
        if (updated == 0) {
            throw new BusinessException("可用量不足，当前可用 " + inv.getQtyAvailable());
        }

        writeTx(inv.getSkuId(), inv.getLocationId(), 0, "FREEZE",
                "INVENTORY", inventoryId,
                "冻结 " + qty + " 件" + (StringUtils.hasText(reason) ? "：" + reason : ""));
        log.info("冻结库存 id={} qty={} 原因={}", inventoryId, qty, reason);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unfreeze(Long inventoryId, Integer qty) {
        if (qty == null || qty <= 0) {
            throw new BusinessException("解冻数量必须大于 0");
        }
        Inventory inv = inventoryMapper.selectById(inventoryId);
        if (inv == null) {
            throw new BusinessException(404, "库存记录不存在");
        }

        int updated = inventoryMapper.unfreezeQty(inventoryId, qty);
        if (updated == 0) {
            throw new BusinessException("冻结量不足，当前冻结 " + inv.getQtyOnhold());
        }

        writeTx(inv.getSkuId(), inv.getLocationId(), 0, "RELEASE",
                "INVENTORY", inventoryId, "解冻 " + qty + " 件");
        log.info("解冻库存 id={} qty={}", inventoryId, qty);
    }

    // ==================================================================
    //  辅助
    // ==================================================================

    private void writeTx(Long skuId, Long locationId, int delta, String bizType,
                         String refType, Long refId, String remark) {
        InventoryTransaction tx = new InventoryTransaction();
        tx.setSkuId(skuId);
        tx.setLocationId(locationId);
        tx.setQtyDelta(delta);
        tx.setBizType(bizType);
        tx.setReferenceType(refType);
        tx.setReferenceId(refId);
        tx.setRemark(remark);
        tx.setCreatedBy("admin");
        transactionMapper.insert(tx);
    }

    private <T> IPage<InventoryVO> emptyPage(InventoryQuery q) {
        return new Page<>(q.getPageNum(), q.getPageSize(), 0);
    }

    private Map<Long, ProductSku> loadSkus(List<Inventory> list) {
        if (list.isEmpty()) return Map.of();
        return skuMapper.selectBatchIds(list.stream()
                        .map(Inventory::getSkuId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(ProductSku::getId, s -> s));
    }

    private Map<Long, String> loadProductRefs(Collection<ProductSku> skus) {
        if (skus.isEmpty()) return Map.of();
        return productMapper.selectBatchIds(skus.stream()
                        .map(ProductSku::getProductId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Product::getId, Product::getReference));
    }

    private Map<Long, Location> loadLocations(List<Inventory> list) {
        if (list.isEmpty()) return Map.of();
        return locationMapper.selectBatchIds(list.stream()
                        .map(Inventory::getLocationId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Location::getId, l -> l));
    }

    private InventoryVO toVO(Inventory i, Map<Long, ProductSku> skuMap,
                             Map<Long, String> prodMap,
                             Map<Long, Location> locMap, Map<Long, String> areaMap) {
        InventoryVO vo = new InventoryVO();
        vo.setId(i.getId());
        vo.setSkuId(i.getSkuId());
        ProductSku sku = skuMap.get(i.getSkuId());
        if (sku != null) {
            vo.setSkuCode(sku.getSkuCode());
            vo.setSizeUs(sku.getSizeUs());
            vo.setReference(prodMap.get(sku.getProductId()));
        }
        vo.setLocationId(i.getLocationId());
        Location loc = locMap.get(i.getLocationId());
        if (loc != null) {
            vo.setLocationCode(loc.getLocationCode());
            vo.setAreaCode(areaMap.get(loc.getAreaId()));
            vo.setLocationTypeName(loc.getLocationType() != null && loc.getLocationType() == 1
                    ? "拣货区" : "存储区");
        }
        vo.setQty(i.getQty());
        vo.setQtyAllocated(i.getQtyAllocated());
        vo.setQtyPicked(i.getQtyPicked());
        vo.setQtyOnhold(i.getQtyOnhold());
        vo.setQtyAvailable(i.getQtyAvailable());
        vo.setVersion(i.getVersion());
        return vo;
    }
}
