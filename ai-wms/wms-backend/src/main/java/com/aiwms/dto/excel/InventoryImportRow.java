package com.aiwms.dto.excel;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

/**
 * 期初库存导入行
 *
 * <p>模板格式：
 * <pre>
 * SKU编码*      库位号*    数量*
 * 8N10W9-41     A-14-11    100
 * </pre>
 *
 * <p>用途：WMS 上线时把仓库现有库存录入系统（叫「期初库存」或「初始盘点」）。
 * 导入后系统会自动写一条 RECEIPT 流水，保证库存与流水能对上账。
 */
@Data
public class InventoryImportRow {

    @ExcelProperty("SKU编码")
    private String skuCode;

    @ExcelProperty("库位号")
    private String locationCode;

    @ExcelProperty("数量")
    private Integer qty;
}
