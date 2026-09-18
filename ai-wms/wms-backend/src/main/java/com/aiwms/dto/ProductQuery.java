package com.aiwms.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 商品列表查询参数
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ProductQuery extends PageQuery {

    /** 款号（模糊查询） */
    private String reference;

    /** ABC 分类（精确匹配） */
    private String abcClass;
}
