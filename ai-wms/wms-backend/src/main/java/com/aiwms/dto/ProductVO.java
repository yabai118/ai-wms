package com.aiwms.dto;

import lombok.Data;

import java.util.List;

/**
 * 商品列表返回对象
 *
 * <p>VO = View Object，专门用于返回给前端，与数据库实体解耦。
 * <p>这里额外带上了「尺码数量」，前端列表页不用再单独查一次。
 */
@Data
public class ProductVO {

    private Long id;
    private String reference;
    private String abcClass;
    private String sector;

    /** 该款共有几个尺码 */
    private Integer sizeCount;

    /** 尺码列表（详情接口用） */
    private List<String> sizes;
}
