package com.aiwms.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 生成波次请求
 */
@Data
public class WaveGenerateRequest {

    /** 要合并进这一波的订单 ID 列表 */
    @NotEmpty(message = "订单列表不能为空")
    private List<Long> orderIds;

    /** 拣货员 ID（可选，不传则自动分配） */
    private Long operatorId;
}
