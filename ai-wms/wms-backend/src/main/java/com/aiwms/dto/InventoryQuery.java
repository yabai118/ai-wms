package com.aiwms.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class InventoryQuery extends PageQuery {

    /** SKU 编码（模糊） */
    private String skuCode;

    /** 款号（模糊） */
    private String reference;

    /** 库位号（模糊） */
    private String locationCode;

    /** 只显示有可用库存的 */
    private Boolean onlyAvailable;

    /** 只显示有已分配量的 */
    private Boolean onlyAllocated;
}
