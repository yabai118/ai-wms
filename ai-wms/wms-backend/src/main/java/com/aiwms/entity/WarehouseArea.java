package com.aiwms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 库区（18 个，A~R）
 */
@Data
@TableName("warehouse_area")
public class WarehouseArea {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 区号 A~R */
    private String areaCode;

    private String areaName;

    private LocalDateTime createdAt;
}
