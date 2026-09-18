package com.aiwms.service;

import com.aiwms.dto.InventoryQuery;
import com.aiwms.dto.OutboundOrderQuery;

import java.io.OutputStream;

/**
 * 数据导出服务
 *
 * <p>导出是 WMS 的标配能力——业务人员需要把数据导到 Excel 里
 * 做二次分析、对账、上报。与「数据导入」配成一对。
 */
public interface ExportService {

    /** 导出库存（含五字段） */
    void exportInventory(InventoryQuery query, OutputStream out);

    /** 导出出库订单 */
    void exportOrders(OutboundOrderQuery query, OutputStream out);

    /** 导出某波次的拣货任务单 */
    void exportPickTasks(Long waveId, OutputStream out);
}
