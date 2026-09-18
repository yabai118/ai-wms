package com.aiwms.service.impl;

import com.aiwms.common.BusinessException;
import com.aiwms.dto.InventoryQuery;
import com.aiwms.dto.InventoryVO;
import com.aiwms.dto.OutboundOrderQuery;
import com.aiwms.dto.OutboundOrderVO;
import com.aiwms.dto.excel.InventoryExportRow;
import com.aiwms.dto.excel.OrderExportRow;
import com.aiwms.dto.excel.PickTaskExportRow;
import com.aiwms.entity.PickingWave;
import com.aiwms.mapper.PickingWaveMapper;
import com.aiwms.service.ExportService;
import com.aiwms.service.InventoryService;
import com.aiwms.service.OutboundService;
import com.aiwms.service.WaveService;
import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.OutputStream;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * 数据导出实现
 *
 * ## 为什么用「分批查询 + 分批写入」而不是一次性查全部
 *
 * 一开始我直接用 `pageSize = 50000` 想一次查完，结果只导出了 500 行——
 * 因为分页插件配了 `maxLimit(500)`（防止单页查太多），把导出也限制住了。
 *
 * **但正确的解法不是把上限抬高**，而是分批：
 *   ① 一次性查 3 万条进内存 → 内存压力大、GC 频繁
 *   ② 分批查（每批 500 条）+ 流式写入 Excel → 内存占用恒定
 *
 * 这也是 EasyExcel 的设计初衷——它支持多次 `write()` 写同一个 sheet。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExportServiceImpl implements ExportService {

    private final InventoryService inventoryService;
    private final OutboundService outboundService;
    private final WaveService waveService;
    private final PickingWaveMapper waveMapper;

    /** 每批查询条数（不超过分页插件的 maxLimit） */
    private static final int BATCH_SIZE = 500;

    /** 安全上限：最多导出 10 万条，防止误操作导出全表把服务拖垮 */
    private static final int MAX_TOTAL = 100_000;

    // ==================================================================
    //  ① 导出库存
    // ==================================================================

    @Override
    public void exportInventory(InventoryQuery query, OutputStream out) {
        exportInBatches(
                out, "库存明细", InventoryExportRow.class,
                (pageNum) -> {
                    query.setPageNum(pageNum);
                    query.setPageSize(BATCH_SIZE);
                    return inventoryService.pageInventory(query);
                },
                this::toInventoryRow);
    }

    private InventoryExportRow toInventoryRow(InventoryVO v) {
        InventoryExportRow r = new InventoryExportRow();
        r.setSkuCode(v.getSkuCode());
        r.setReference(v.getReference());
        r.setSizeUs(v.getSizeUs() == null ? "" : v.getSizeUs().stripTrailingZeros().toPlainString());
        r.setLocationCode(v.getLocationCode());
        r.setAreaCode(v.getAreaCode());
        r.setLocationType(v.getLocationTypeName());
        r.setQty(v.getQty());
        r.setQtyAllocated(v.getQtyAllocated());
        r.setQtyPicked(v.getQtyPicked());
        r.setQtyOnhold(v.getQtyOnhold());
        r.setQtyAvailable(v.getQtyAvailable());
        return r;
    }

    // ==================================================================
    //  ② 导出订单
    // ==================================================================

    @Override
    public void exportOrders(OutboundOrderQuery query, OutputStream out) {
        exportInBatches(
                out, "出库订单", OrderExportRow.class,
                (pageNum) -> {
                    query.setPageNum(pageNum);
                    query.setPageSize(BATCH_SIZE);
                    return outboundService.pageOrders(query);
                },
                this::toOrderRow);
    }

    private OrderExportRow toOrderRow(OutboundOrderVO v) {
        OrderExportRow r = new OrderExportRow();
        r.setOrderNo(v.getOrderNo());
        r.setCustomerCode(v.getCustomerCode());
        r.setStatusName(v.getStatusName());
        r.setLineCount(v.getLineCount());
        r.setTotalQty(v.getTotalQty());
        r.setOrderTime(v.getOrderTime());
        return r;
    }

    // ==================================================================
    //  ③ 导出拣货任务（数据量小，一次写完）
    // ==================================================================

    @Override
    public void exportPickTasks(Long waveId, OutputStream out) {
        PickingWave wave = waveMapper.selectById(waveId);
        if (wave == null) {
            throw new BusinessException(404, "波次不存在: " + waveId);
        }

        List<PickTaskExportRow> rows = waveService.listTasks(waveId).stream().map(t -> {
            PickTaskExportRow r = new PickTaskExportRow();
            r.setSeqNo(t.getSeqNo() == null ? "" : String.valueOf(t.getSeqNo()));
            r.setLocationCode(t.getLocationCode());
            r.setSkuCode(t.getSkuCode());
            r.setQtyPlan(t.getQtyPlan());
            r.setQtyPicked(t.getQtyPicked());
            r.setStatusName(t.getStatusName());
            r.setCoord("(" + t.getXCoord() + ", " + t.getYCoord() + ")");
            return r;
        }).toList();

        EasyExcel.write(out, PickTaskExportRow.class)
                .sheet("拣货任务-" + wave.getWaveNo()).doWrite(rows);

        log.info("导出拣货任务: 波次 {} 共 {} 行", wave.getWaveNo(), rows.size());
    }

    // ==================================================================
    //  通用：分批查询 + 流式写入
    // ==================================================================

    /**
     * 分批导出通用逻辑
     *
     * @param out        输出流
     * @param sheetName  工作表名
     * @param clazz      Excel 行类型
     * @param pageLoader 分页加载函数（输入页码，返回分页结果）
     * @param converter  实体 → Excel 行
     */
    private <T, R> void exportInBatches(OutputStream out,
                                        String sheetName,
                                        Class<R> clazz,
                                        Function<Integer, IPage<T>> pageLoader,
                                        Function<T, R> converter) {
        ExcelWriter writer = EasyExcel.write(out, clazz).build();
        WriteSheet sheet = EasyExcel.writerSheet(sheetName).build();

        int pageNum = 1;
        int total = 0;

        try {
            while (true) {
                IPage<T> page = pageLoader.apply(pageNum);
                List<T> records = page.getRecords();

                if (records == null || records.isEmpty()) {
                    break;                       // 没有数据了，结束
                }

                writer.write(records.stream().map(converter).toList(), sheet);
                total += records.size();

                if (records.size() < BATCH_SIZE) {
                    break;                       // 最后一批（不满）
                }
                if (total >= MAX_TOTAL) {
                    log.warn("导出达到上限 {} 条，提前结束", MAX_TOTAL);
                    break;
                }
                pageNum++;
            }
        } finally {
            writer.finish();
        }

        log.info("导出 {}: {} 行", sheetName, total);
    }
}
