package com.aiwms.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 上架请求（为每条明细指定货位）
 *
 * <p>货位可以不传 —— 系统会自动推荐
 */
@Data
public class InboundShelveRequest {

    @NotEmpty(message = "上架明细不能为空")
    @Valid
    private List<Item> items;

    @Data
    public static class Item {
        @NotNull(message = "明细 ID 不能为空")
        private Long lineId;

        /** 目标货位（不传则由系统推荐） */
        private Long locationId;
    }
}
