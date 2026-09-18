package com.aiwms.dto;

import lombok.Data;

/**
 * 库位地图点位（精简字段，用于前端绘图）
 *
 * <p>地图页要一次渲染 2,314 个点，字段越少传输越快。
 */
@Data
public class LocationMapVO {

    private String code;        // 库位号
    private Integer x;          // X 坐标
    private Integer y;          // Y 坐标
    private Integer z;          // 层
    private Integer type;       // 库位类型
    private String area;        // 库区
}
