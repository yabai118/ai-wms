package com.aiwms.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 库位列表查询参数
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class LocationQuery extends PageQuery {

    /** 库位号（模糊） */
    private String locationCode;

    /** 所属库区 */
    private Long areaId;

    /** 库位类型：0存储区 1拣货区 */
    private Integer locationType;

    /** 状态：0空闲 1占用 2锁定 */
    private Integer status;
}
