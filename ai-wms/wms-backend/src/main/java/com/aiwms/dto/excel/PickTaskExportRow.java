package com.aiwms.dto.excel;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

/**
 * 拣货任务导出行（拣货单）
 *
 * <p>实际仓库会把这张表打印出来给拣货员用（或用 PDA 看），
 * 拣完一项划掉一项。
 */
@Data
@ColumnWidth(16)
public class PickTaskExportRow {

    @ExcelProperty("拣货顺序")
    private String seqNo;

    @ExcelProperty("库位号")
    private String locationCode;

    @ExcelProperty("SKU编码")
    private String skuCode;

    @ExcelProperty("应拣数量")
    private Integer qtyPlan;

    @ExcelProperty("已拣数量")
    private Integer qtyPicked;

    @ExcelProperty("状态")
    private String statusName;

    @ExcelProperty("库位坐标")
    private String coord;
}
