package com.aiwms.dto;

import lombok.Data;

/**
 * 拣货任务返回对象（含库位坐标，供路径优化用）
 */
@Data
public class PickingTaskVO {

    private Long id;
    private Long skuId;
    private String skuCode;
    private Long locationId;
    private String locationCode;
    private Integer xCoord;
    private Integer yCoord;
    private Integer zCoord;
    private Integer qtyPlan;
    private Integer qtyPicked;
    private Integer seqNo;
    private Integer status;
    private String statusName;
}
