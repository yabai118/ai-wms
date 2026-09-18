package com.aiwms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 拣货员（24 个）
 */
@Data
@TableName("operator")
public class Operator {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String opCode;

    private String opName;

    private LocalDateTime createdAt;
}
