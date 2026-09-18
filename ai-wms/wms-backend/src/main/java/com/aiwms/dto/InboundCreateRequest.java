package com.aiwms.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 创建入库单请求
 */
@Data
public class InboundCreateRequest {

    /** 1生产入库 2退货入库 */
    private Integer orderType = 1;

    private String sourceNo;

    private LocalDate expectedDate;

    private String remark;

    @NotEmpty(message = "入库明细不能为空")
    @Valid
    private List<Line> lines;

    @Data
    public static class Line {
        @NotNull(message = "SKU 不能为空")
        private Long skuId;

        @NotNull(message = "计划数量不能为空")
        @Min(value = 1, message = "计划数量必须大于 0")
        private Integer planQty;
    }
}
