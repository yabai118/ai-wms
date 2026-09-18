package com.aiwms.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 收货请求（填实收数量）
 */
@Data
public class InboundReceiveRequest {

    @NotEmpty(message = "收货明细不能为空")
    @Valid
    private List<Item> items;

    @Data
    public static class Item {
        @NotNull(message = "明细 ID 不能为空")
        private Long lineId;

        /** 实收数量（可以少于计划数量） */
        @NotNull(message = "实收数量不能为空")
        @Min(value = 0, message = "实收数量不能为负")
        private Integer receivedQty;
    }
}
