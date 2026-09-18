package com.aiwms.dto.excel;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

/**
 * 商品导入行（Excel 一行 = 一个 SKU）
 *
 * <p>模板格式：
 * <pre>
 * 款号*      ABC分类*   尺码*     SKU编码(可选)
 * 8N10W9     A          41        8N10W9-41
 * 8N10W9     A          42        (留空则自动生成)
 * </pre>
 */
@Data
public class ProductImportRow {

    @ExcelProperty("款号")
    private String reference;

    @ExcelProperty("ABC分类")
    private String abcClass;

    @ExcelProperty("尺码")
    private String sizeUs;

    @ExcelProperty("SKU编码")
    private String skuCode;
}
