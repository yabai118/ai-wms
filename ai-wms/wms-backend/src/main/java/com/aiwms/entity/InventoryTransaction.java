package com.aiwms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 库存流水（可追溯、可对账）★
 *
 * <p>每一次库存变动都记一条，包含：
 * <ul>
 *   <li>变动量 qty_delta（正负）</li>
 *   <li>业务类型 biz_type（RECEIPT/PICK/SHIP/...）</li>
 *   <li><b>来源单据 reference_type + reference_id</b> —— 溯源的关键</li>
 * </ul>
 *
 * <p>用途：① 排查问题（库存怎么变成这样的）② 对账（用流水累加重算库存）
 */
@Data
@TableName("inventory_transaction")
public class InventoryTransaction {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long skuId;

    private Long locationId;

    /** 变动量（正负） */
    private Integer qtyDelta;

    /** RECEIPT/PICK/SHIP/ADJUST/FREEZE/RELEASE/ALLOCATE */
    private String bizType;

    /** 来源单据类型 */
    private String referenceType;

    /** 来源单据 ID */
    private Long referenceId;

    private String remark;

    private String createdBy;

    private LocalDateTime createdAt;
}
