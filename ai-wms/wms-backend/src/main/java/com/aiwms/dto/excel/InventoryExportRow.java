package com.aiwms.dto.excel;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

/**
 * 库存导出行
 *
 * <p>导出字段与页面显示一致，便于业务人员直接在 Excel 里做二次分析。
 */
@Data
@ColumnWidth(16)
public class InventoryExportRow {

    @ExcelProperty("SKU编码")
    private String skuCode;

    @ExcelProperty("款号")
    private String reference;

    @ExcelProperty("尺码")
    private String sizeUs;

    @ExcelProperty("库位号")
    private String locationCode;

    @ExcelProperty("库区")
    private String areaCode;

    @ExcelProperty("区域类型")
    private String locationType;

    @ExcelProperty("现有量")
    private Integer qty;

    @ExcelProperty("已分配")
    private Integer qtyAllocated;

    @ExcelProperty("已拣出")
    private Integer qtyPicked;

    @ExcelProperty("冻结")
    private Integer qtyOnhold;

    @ExcelProperty("可用量")
    private Integer qtyAvailable;
}
