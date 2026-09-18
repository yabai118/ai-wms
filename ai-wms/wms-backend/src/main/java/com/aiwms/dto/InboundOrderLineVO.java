package com.aiwms.dto;

import lombok.Data;

/**
 * 入库明细返回对象
 */
@Data
public class InboundOrderLineVO {

    private Long id;
    private Long skuId;

    /** SKU 编码，如 8N10W9-41 */
    private String skuCode;

    /** 商品款号 */
    private String reference;

    /** 尺码 */
    private java.math.BigDecimal sizeUs;

    private Integer planQty;
    private Integer receivedQty;

    private Long locationId;
    private String locationCode;

    private Integer status;
    private String statusName;
}
