package com.aiwms.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 拣货任务返回对象（含库位坐标，供路径优化用）
 *
 * <p>⚠️ 注意：xCoord / yCoord / zCoord 必须显式加 @JsonProperty。
 * <p>Jackson 对 getXCoord() 这类「单字母前缀 + 大写」的命名会解析成 xcoord（全小写），
 * 导致前端取不到值。这是本项目踩过的坑（库位页面也遇到过）。
 */
@Data
public class PickingTaskVO {

    private Long id;
    private Long skuId;
    private String skuCode;
    private Long locationId;
    private String locationCode;

    @JsonProperty("xCoord")
    private Integer xCoord;

    @JsonProperty("yCoord")
    private Integer yCoord;

    @JsonProperty("zCoord")
    private Integer zCoord;

    private Integer qtyPlan;
    private Integer qtyPicked;
    private Integer seqNo;
    private Integer status;
    private String statusName;
}
