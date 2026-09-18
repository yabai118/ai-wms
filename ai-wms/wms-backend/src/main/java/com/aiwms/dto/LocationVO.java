package com.aiwms.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 库位返回对象
 *
 * <p>注意：xCoord / yCoord / zCoord 必须显式加 @JsonProperty。
 * <p>原因：Jackson 对 getXCoord() 这类「首字母+大写」的命名解析异常，
 * 会序列化成 xcoord（全小写），导致前端取不到值。
 */
@Data
public class LocationVO {

    private Long id;

    /** 库位号，如 A-14-11 */
    private String locationCode;

    /** 库区编号，如 A */
    private String areaCode;

    /** 库位类型：0存储区 1拣货区 2收货区 3发货区 */
    private Integer locationType;

    /** 类型名称（中文） */
    private String locationTypeName;

    @JsonProperty("xCoord")
    private Integer xCoord;

    @JsonProperty("yCoord")
    private Integer yCoord;

    @JsonProperty("zCoord")
    private Integer zCoord;

    private Integer capacity;
    private Integer usedSlots;

    /** 占用率（%） */
    private Integer usageRate;

    private Integer status;
    private String statusName;
}
