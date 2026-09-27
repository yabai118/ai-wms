package com.aiwms.dto;

import lombok.Data;

/**
 * 客户返回对象
 *
 * <p>主要供新建出库单时选择客户用。
 */
@Data
public class CustomerVO {

    private Long id;

    /** 客户编码 */
    private String custCode;

    private String custName;
}
