package com.aiwms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 库位
 *
 * <p>2,314 个库位，含真实三维坐标（来自企业 WMS 导出数据）
 * <p>location_type 由数据探索得出：区使用率 0% → 存储区，>0% → 拣货区
 */
@Data
@TableName("location")
public class Location {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 库位号，如 A-14-11 */
    private String locationCode;

    /** 所属库区 */
    private Long areaId;

    /** 0存储区(储备) 1拣货区 2收货区 3发货区 */
    private Integer locationType;

    /** X 坐标（米） */
    private Integer xCoord;

    /** Y 坐标（米） */
    private Integer yCoord;

    /** 层（1~4） */
    private Integer zCoord;

    /** 容量（商品位数，真实为 18） */
    private Integer capacity;

    /** 已用商品位数 */
    private Integer usedSlots;

    /** 0空闲 1占用 2锁定 */
    private Integer status;

    private LocalDateTime createdAt;
}
