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

    /**
     * 拣货员（员工 id）
     *
     * <p><b>正常不传</b> —— 后端从当前登录用户取（{@code UserContext.staffId()}），
     * 这样前端无法冒名。这个字段只是兜底，留作将来「组长代操作」之类场景的入口。
     */
    private Long staffId;
}
