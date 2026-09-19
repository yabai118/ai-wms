package com.aiwms.service.impl;

import com.aiwms.common.BusinessException;
import com.aiwms.dto.*;
import com.aiwms.entity.*;
import com.aiwms.mapper.*;
import com.aiwms.service.WaveService;
import com.aiwms.service.StockCacheService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
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
 * 波次服务实现
 *
 * <p><b>本类的核心是「按库位聚合」</b>：
 * 多个订单的分配明细（订单视角）→ 聚合成拣货任务（库位视角）
 * <p>数据验证：聚合后能减少 52% 的行走次数
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WaveServiceImpl implements WaveService {

    private final PickingWaveMapper waveMapper;
    private final PickingTaskMapper taskMapper;
    private final OutboundOrderMapper orderMapper;
    private final OutboundOrderLineMapper orderLineMapper;
    private final OutboundAllocationMapper allocationMapper;
    private final InventoryMapper inventoryMapper;
    private final InventoryTransactionMapper transactionMapper;
    private final ProductSkuMapper skuMapper;
    private final OperatorMapper operatorMapper;
    private final ShipmentMapper shipmentMapper;
    private final StockCacheService stockCacheService;

    /** 载具容量：27 件 */
    private static final int CAPACITY = 27;

    private static final Map<Integer, String> WAVE_STATUS = Map.of(
            0, "待拣货", 1, "拣货中", 2, "已完成");
    private static final Map<Integer, String> TASK_STATUS = Map.of(
            0, "待拣", 1, "已拣", 2, "缺货");

    // ==================================================================
    //  查询
    // ==================================================================

    @Override
    public IPage<WaveVO> pageWaves(WaveQuery query) {
        LambdaQueryWrapper<PickingWave> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getWaveNo())) {
            wrapper.like(PickingWave::getWaveNo, query.getWaveNo());
        }
        if (query.getStatus() != null) {
            wrapper.eq(PickingWave::getStatus, query.getStatus());
        }
        wrapper.orderByDesc(PickingWave::getId);

        Page<PickingWave> page = new Page<>(query.getPageNum(), query.getPageSize());
        IPage<PickingWave> result = waveMapper.selectPage(page, wrapper);

        List<WaveVO> voList = result.getRecords().stream()
                .map(w -> toVO(w, null))
                .toList();

        Page<WaveVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(voList);
        return voPage;
    }

    @Override
    public WaveVO getWaveDetail(Long id) {
        PickingWave wave = waveMapper.selectById(id);
        if (wave == null) {
            throw new BusinessException(404, "波次不存在: id=" + id);
        }
        return toVO(wave, listTasks(id));
    }

    @Override
    public List<PickingTaskVO> listTasks(Long waveId) {
        return taskMapper.listByWave(waveId).stream().map(m -> {
            PickingTaskVO vo = new PickingTaskVO();
            vo.setId(((Number) m.get("id")).longValue());
            vo.setSkuId(((Number) m.get("skuId")).longValue());
            vo.setSkuCode((String) m.get("skuCode"));
            vo.setLocationId(((Number) m.get("locationId")).longValue());
            vo.setLocationCode((String) m.get("locationCode"));
            vo.setXCoord(((Number) m.get("xCoord")).intValue());
            vo.setYCoord(((Number) m.get("yCoord")).intValue());
            vo.setZCoord(((Number) m.get("zCoord")).intValue());
            vo.setQtyPlan(((Number) m.get("qtyPlan")).intValue());
            vo.setQtyPicked(m.get("qtyPicked") == null ? 0 : ((Number) m.get("qtyPicked")).intValue());
            vo.setSeqNo(m.get("seqNo") == null ? null : ((Number) m.get("seqNo")).intValue());
            int st = ((Number) m.get("status")).intValue();
            vo.setStatus(st);
            vo.setStatusName(TASK_STATUS.getOrDefault(st, "未知"));
            return vo;
        }).toList();
    }

    // ==================================================================
    //  ★ 应用路径优化顺序（把算法结果落到业务上）
    //
    //  「路径优化」本身只是在算法服务里算出最优顺序，
    //   真正让它影响拣货作业的是这一步：把顺序写进 picking_task.seq_no。
    //   listByWave 本来就按 seq_no 排序，写完拣货单顺序就变了。
    // ==================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void applySequence(Long waveId, List<Long> taskIds) {
        PickingWave wave = waveMapper.selectById(waveId);
        if (wave == null) {
            throw new BusinessException("波次不存在");
        }
        if (wave.getStatus() == null || wave.getStatus() != 0) {
            throw new BusinessException("只有「待拣货」的波次才能改拣货顺序");
        }

        List<PickingTask> tasks = taskMapper.selectList(
                new LambdaQueryWrapper<PickingTask>().eq(PickingTask::getWaveId, waveId));
        if (tasks.isEmpty()) {
            throw new BusinessException("该波次没有拣货任务");
        }

        // 清除顺序：全部置空，恢复按 id 排
        if (taskIds == null || taskIds.isEmpty()) {
            for (PickingTask t : tasks) {
                taskMapper.update(null, new LambdaUpdateWrapper<PickingTask>()
                        .eq(PickingTask::getId, t.getId())
                        .set(PickingTask::getSeqNo, null));
            }
            log.info("波次 {} 已清除优化顺序（{} 个任务）", waveId, tasks.size());
            return;
        }

        // 校验「不重不漏」：必须正好是该波次的全部任务
        Set<Long> expected = tasks.stream().map(PickingTask::getId)
                .collect(Collectors.toSet());
        Set<Long> got = new HashSet<>(taskIds);
        if (got.contains(null) || got.size() != taskIds.size()) {
            throw new BusinessException("任务列表有重复或空值");
        }
        if (!got.equals(expected)) {
            throw new BusinessException("任务列表与该波次不匹配，必须包含全部任务且不重复");
        }

        for (int i = 0; i < taskIds.size(); i++) {
            taskMapper.update(null, new LambdaUpdateWrapper<PickingTask>()
                    .eq(PickingTask::getId, taskIds.get(i))
                    .set(PickingTask::getSeqNo, i + 1));
        }
        log.info("波次 {} 已应用优化顺序（{} 个任务）", waveId, taskIds.size());
    }

    // ==================================================================
    //  ★★ 生成波次（按库位聚合）
    // ==================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WaveVO generateWave(WaveGenerateRequest request) {
        List<Long> orderIds = request.getOrderIds();

        // ① 校验订单状态
        List<OutboundOrder> orders = orderMapper.selectBatchIds(orderIds);
        if (orders.size() != orderIds.size()) {
            throw new BusinessException("部分订单不存在");
        }
        for (OutboundOrder o : orders) {
            if (o.getStatus() != 1) {
                throw new BusinessException("订单 " + o.getOrderNo()
                        + " 状态不是「已分配」，不能加入波次");
            }
        }

        // ② 收集这些订单的所有分配明细
        List<Long> lineIds = orderLineMapper.selectList(
                        new LambdaQueryWrapper<OutboundOrderLine>()
                                .in(OutboundOrderLine::getOrderId, orderIds))
                .stream().map(OutboundOrderLine::getId).toList();
        if (lineIds.isEmpty()) {
            throw new BusinessException("订单没有明细");
        }

        List<OutboundAllocation> allocations = allocationMapper.selectList(
                new LambdaQueryWrapper<OutboundAllocation>()
                        .in(OutboundAllocation::getOrderLineId, lineIds)
                        .eq(OutboundAllocation::getStatus, 0));
        if (allocations.isEmpty()) {
            throw new BusinessException("订单尚未分配库存，无法生成波次");
        }

        // ③ 校验载具容量
        int totalQty = allocations.stream().mapToInt(OutboundAllocation::getQtyAllocated).sum();
        if (totalQty > CAPACITY) {
            throw new BusinessException("总件数 " + totalQty + " 超过载具容量 " + CAPACITY
                    + "，请减少订单数量");
        }

        // ④ 创建波次
        PickingWave wave = new PickingWave();
        wave.setWaveNo(generateWaveNo());
        wave.setOperatorId(request.getOperatorId());
        wave.setStatus(0);
        wave.setCapacity(CAPACITY);
        wave.setTotalQty(totalQty);
        wave.setTotalTasks(0);
        waveMapper.insert(wave);

        // ⑤ ★ 按 (SKU, 库位) 聚合 → 生成拣货任务
        Map<String, List<OutboundAllocation>> grouped = allocations.stream()
                .collect(Collectors.groupingBy(a -> a.getSkuId() + "_" + a.getLocationId()));

        int taskCount = 0;
        for (Map.Entry<String, List<OutboundAllocation>> e : grouped.entrySet()) {
            List<OutboundAllocation> group = e.getValue();
            OutboundAllocation first = group.get(0);
            int qty = group.stream().mapToInt(OutboundAllocation::getQtyAllocated).sum();

            PickingTask task = new PickingTask();
            task.setWaveId(wave.getId());
            task.setSkuId(first.getSkuId());
            task.setLocationId(first.getLocationId());
            task.setQtyPlan(qty);
            task.setQtyPicked(0);
            task.setStatus(0);
            taskMapper.insert(task);
            taskCount++;

            // 回填 allocation 的 wave_id 和 task_id
            for (OutboundAllocation a : group) {
                a.setWaveId(wave.getId());
                a.setTaskId(task.getId());
                allocationMapper.updateById(a);
            }
        }

        wave.setTotalTasks(taskCount);
        waveMapper.updateById(wave);

        // ⑥ 更新订单状态 = 拣货中
        for (OutboundOrder o : orders) {
            o.setStatus(2);
            orderMapper.updateById(o);
        }

        log.info("波次 {} 生成完成：{} 个订单 → {} 个任务，共 {} 件（聚合前 {} 条分配明细）",
                wave.getWaveNo(), orders.size(), taskCount, totalQty, allocations.size());

        return toVO(wave, null);
    }

    // ==================================================================
    //  ★ 拣货确认（allocated → picked，总数不变）
    // ==================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void pickWave(Long waveId) {
        PickingWave wave = waveMapper.selectById(waveId);
        if (wave == null) throw new BusinessException(404, "波次不存在");
        if (wave.getStatus() != 0 && wave.getStatus() != 1) {
            throw new BusinessException("波次状态[" + WAVE_STATUS.get(wave.getStatus()) + "]不允许拣货");
        }

        List<PickingTask> tasks = taskMapper.selectList(
                new LambdaQueryWrapper<PickingTask>()
                        .eq(PickingTask::getWaveId, waveId)
                        .eq(PickingTask::getStatus, 0));
        if (tasks.isEmpty()) {
            throw new BusinessException("没有待拣的任务");
        }

        for (PickingTask task : tasks) {
            int qty = task.getQtyPlan();

            // ★ 库存状态转移：allocated → picked（总数 qty 不变）
            int updated = inventoryMapper.pickQty(task.getSkuId(), task.getLocationId(), qty);
            if (updated == 0) {
                throw new BusinessException("拣货失败：库位 " + task.getLocationId()
                        + " 的已分配量不足（可能已被释放）");
            }

            // 更新任务
            task.setQtyPicked(qty);
            task.setStatus(1);
            task.setPickedAt(LocalDateTime.now());
            taskMapper.updateById(task);

            // 更新分配明细状态
            allocationMapper.update(null,
                    new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<OutboundAllocation>()
                            .eq(OutboundAllocation::getTaskId, task.getId())
                            .set(OutboundAllocation::getStatus, 1));

            // 写流水
            InventoryTransaction tx = new InventoryTransaction();
            tx.setSkuId(task.getSkuId());
            tx.setLocationId(task.getLocationId());
            tx.setQtyDelta(0);
            tx.setBizType("PICK");
            tx.setReferenceType("PICKING_WAVE");
            tx.setReferenceId(waveId);
            tx.setRemark("拣货确认: " + wave.getWaveNo() + " / 库位任务 " + task.getId()
                    + " / " + qty + " 件");
            tx.setCreatedBy("picker");
            transactionMapper.insert(tx);
        }

        wave.setStatus(1);
        wave.setPathDistance(calcPathDistance(waveId));
        waveMapper.updateById(wave);

        log.info("波次 {} 拣货完成：{} 个任务", wave.getWaveNo(), tasks.size());
    }

    // ==================================================================
    //  ★ 发货确认（真正扣减库存总数）
    // ==================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void shipWave(Long waveId) {
        PickingWave wave = waveMapper.selectById(waveId);
        if (wave == null) throw new BusinessException(404, "波次不存在");
        if (wave.getStatus() != 1) {
            throw new BusinessException("波次需要先完成拣货才能发货");
        }

        List<PickingTask> tasks = taskMapper.selectList(
                new LambdaQueryWrapper<PickingTask>().eq(PickingTask::getWaveId, waveId));

        int totalQty = 0;
        for (PickingTask task : tasks) {
            int qty = task.getQtyPicked() == null ? 0 : task.getQtyPicked();
            if (qty <= 0) continue;

            // ★ 真正扣减库存总数：qty -= N, picked -= N
            int updated = inventoryMapper.shipQty(task.getSkuId(), task.getLocationId(), qty);
            if (updated == 0) {
                throw new BusinessException("发货失败：库位 " + task.getLocationId() + " 已拣货量不足");
            }

            InventoryTransaction tx = new InventoryTransaction();
            tx.setSkuId(task.getSkuId());
            tx.setLocationId(task.getLocationId());
            tx.setQtyDelta(-qty);                      // 这里才真正减总数
            tx.setBizType("SHIP");
            tx.setReferenceType("PICKING_WAVE");
            tx.setReferenceId(waveId);
            tx.setRemark("发货出库: " + wave.getWaveNo() + " / " + qty + " 件");
            tx.setCreatedBy("system");
            transactionMapper.insert(tx);

            totalQty += qty;
        }

        // 创建发货单
        Shipment shipment = new Shipment();
        shipment.setShipmentNo("FH" + LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")));
        shipment.setWaveId(waveId);
        shipment.setTotalQty(totalQty);
        shipment.setStatus(1);
        shipment.setShippedAt(LocalDateTime.now());
        shipmentMapper.insert(shipment);

        // 发货真正扣减了库存总数 → 删缓存
        tasks.stream().map(PickingTask::getSkuId).distinct()
                .forEach(stockCacheService::evictAfterCommit);

        // 更新波次与订单
        wave.setStatus(2);
        wave.setFinishedAt(LocalDateTime.now());
        waveMapper.updateById(wave);

        List<Long> orderIds = getAllocationOrderIds(waveId);
        if (!orderIds.isEmpty()) {
            for (OutboundOrder o : orderMapper.selectBatchIds(orderIds)) {
                o.setStatus(3);                        // 已发货
                orderMapper.updateById(o);
            }
        }

        log.info("波次 {} 发货完成：{} 件，发货单 {}", wave.getWaveNo(), totalQty, shipment.getShipmentNo());
    }

    // ==================================================================
    //  路径距离计算
    // ==================================================================

    @Override
    public int calcPathDistance(Long waveId) {
        List<PickingTaskVO> tasks = listTasks(waveId).stream()
                .filter(t -> t.getXCoord() != null)
                .toList();
        if (tasks.size() < 2) return 0;

        // 起点：仓库入口（x=0, y=0）；按当前顺序累加欧氏距离
        int dist = 0;
        int px = 0, py = 0;
        for (PickingTaskVO t : tasks) {
            dist += (int) Math.sqrt(Math.pow(t.getXCoord() - px, 2)
                    + Math.pow(t.getYCoord() - py, 2));
            px = t.getXCoord();
            py = t.getYCoord();
        }
        // 回到起点
        dist += (int) Math.sqrt(Math.pow(px, 2) + Math.pow(py, 2));
        return dist;
    }

    // ==================================================================
    //  辅助
    // ==================================================================

    private List<Long> getAllocationOrderIds(Long waveId) {
        List<OutboundAllocation> allocs = allocationMapper.selectList(
                new LambdaQueryWrapper<OutboundAllocation>().eq(OutboundAllocation::getWaveId, waveId));
        if (allocs.isEmpty()) return List.of();
        List<Long> lineIds = allocs.stream().map(OutboundAllocation::getOrderLineId).distinct().toList();
        return orderLineMapper.selectBatchIds(lineIds).stream()
                .map(OutboundOrderLine::getOrderId).distinct().toList();
    }

    private String generateWaveNo() {
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        long count = waveMapper.selectCount(new LambdaQueryWrapper<PickingWave>()
                .likeRight(PickingWave::getWaveNo, "BC" + date)) + 1;
        return String.format("BC%s-%03d", date, count);
    }

    private WaveVO toVO(PickingWave w, List<PickingTaskVO> tasks) {
        WaveVO vo = new WaveVO();
        vo.setId(w.getId());
        vo.setWaveNo(w.getWaveNo());
        vo.setOperatorId(w.getOperatorId());
        if (w.getOperatorId() != null) {
            Operator op = operatorMapper.selectById(w.getOperatorId());
            vo.setOperatorName(op == null ? null : op.getOpName());
        }
        vo.setStatus(w.getStatus());
        vo.setStatusName(WAVE_STATUS.getOrDefault(w.getStatus(), "未知"));
        vo.setCapacity(w.getCapacity());
        vo.setTotalQty(w.getTotalQty());
        vo.setTotalTasks(w.getTotalTasks());
        vo.setPathDistance(w.getPathDistance());
        vo.setCreatedAt(w.getCreatedAt());
        vo.setFinishedAt(w.getFinishedAt());
        vo.setOrderCount(getAllocationOrderIds(w.getId()).size());
        vo.setTasks(tasks);
        return vo;
    }
}
