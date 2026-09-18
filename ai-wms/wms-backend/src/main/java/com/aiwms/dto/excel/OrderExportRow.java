package com.aiwms.dto.excel;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.format.DateTimeFormat;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 出库订单导出行
 */
@Data
@ColumnWidth(16)
public class OrderExportRow {

    @ExcelProperty("订单号")
    private String orderNo;

    @ExcelProperty("客户")
    private String customerCode;

    @ExcelProperty("状态")
    private String statusName;

    @ExcelProperty("明细数")
    private Integer lineCount;

    @ExcelProperty("总件数")
    private Integer totalQty;

    @ExcelProperty("下单时间")
    @DateTimeFormat("yyyy-MM-dd HH:mm:ss")
    private LocalDateTime orderTime;
}
