package com.aiwms.dto;

import lombok.Data;

/**
 * 分页查询基类 —— 所有列表查询 DTO 都继承它
 */
@Data
public class PageQuery {

    /** 页码，从 1 开始 */
    private Integer pageNum = 1;

    /** 每页条数 */
    private Integer pageSize = 20;

    /** 排序字段（可选） */
    private String orderBy;

    /** 是否升序 */
    private Boolean asc = false;

    public Integer getPageNum() {
        return (pageNum == null || pageNum < 1) ? 1 : pageNum;
    }

    public Integer getPageSize() {
        if (pageSize == null || pageSize < 1) return 20;
        return pageSize > 500 ? 500 : pageSize;
    }
}
