package com.aiwms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * SKU（款 × 尺码）—— 库存的真正单位
 *
 * <p>鞋类仓储的关键特征：同一款鞋的每个尺码都是**独立库存单位**。
 * <p>例如 8N10W9 这款有 41 码、42 码…共 2,515 个 SKU。
 */
@Data
@TableName("product_sku")
public class ProductSku {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 商品款 ID */
    private Long productId;

    /** 美国码（1.0 ~ 30.5） */
    private BigDecimal sizeUs;

    /** SKU 编码，如 8N10W9-41 */
    private String skuCode;

    private LocalDateTime createdAt;
}
