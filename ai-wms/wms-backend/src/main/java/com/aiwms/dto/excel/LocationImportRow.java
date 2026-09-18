package com.aiwms.dto.excel;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

/**
 * 库位导入行
 *
 * <p>模板格式：
 * <pre>
 * 库位号*   库区*  类型*    X坐标*  Y坐标*  Z坐标*  容量
 * A-14-11   A      拣货区   368     0       1       18
 * </pre>
 * 类型可选值：存储区 / 拣货区 / 收货区 / 发货区
 */
@Data
public class LocationImportRow {

    @ExcelProperty("库位号")
    private String locationCode;

    @ExcelProperty("库区")
    private String areaCode;

    @ExcelProperty("类型")
    private String locationType;

    @ExcelProperty("X坐标")
    private Integer xCoord;

    @ExcelProperty("Y坐标")
    private Integer yCoord;

    @ExcelProperty("Z坐标")
    private Integer zCoord;

    @ExcelProperty("容量")
    private Integer capacity;
}
