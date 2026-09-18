package com.aiwms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商品款
 *
 * <p>对应数据库表 product（208 个鞋款）
 * <p>注意：库存不挂在「款」上，而是挂在 SKU（款×尺码）上，见 {@link ProductSku}
 */
@Data
@TableName("product")
public class Product {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 款号，如 8N10W9 */
    private String reference;

    /** ABC 分类（A/B/C）—— 按作业频率分级，用于货位分配 */
    private String abcClass;

    /** 所属分区 */
    private String sector;

    private LocalDateTime createdAt;
}
