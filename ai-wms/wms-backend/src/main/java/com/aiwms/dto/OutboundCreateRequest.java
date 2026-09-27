package com.aiwms.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 创建出库单请求
 *
 * <p>系统原本的出库单全部来自数据集导入（{@code gen_outbound_data.py} 直接生成 INSERT），
 * 从未有过「创建」这条路。这个 DTO 是补上该能力的第一步。
 */
@Data
public class OutboundCreateRequest {

    @NotNull(message = "客户不能为空")
    private Long customerId;

    @NotEmpty(message = "出库明细不能为空")
    @Valid
    private List<Line> lines;

    @Data
    public static class Line {

        @NotNull(message = "SKU 不能为空")
        private Long skuId;

        @NotNull(message = "数量不能为空")
        @Min(value = 1, message = "数量必须大于 0")
        private Integer qty;
    }
}
