package com.aiwms.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 库存返回对象（五字段模型）★
 */
@Data
public class InventoryVO {

    private Long id;

    private Long skuId;
    private String skuCode;
    private String reference;
    private BigDecimal sizeUs;
    private String abcClass;

    private Long locationId;
    private String locationCode;
    private String areaCode;
    private String locationTypeName;

    /** ★ 五字段 */
    private Integer qty;
    private Integer qtyAllocated;
    private Integer qtyPicked;
    private Integer qtyOnhold;
    private Integer qtyAvailable;

    private Integer version;
}
