package com.aiwms.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * SKU 返回对象（供下拉选择用）
 */
@Data
public class SkuVO {

    private Long id;
    private String skuCode;
    private String reference;
    private BigDecimal sizeUs;
    private String abcClass;
}
