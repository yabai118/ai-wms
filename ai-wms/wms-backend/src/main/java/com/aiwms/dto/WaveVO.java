package com.aiwms.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class WaveVO {

    private Long id;
    private String waveNo;
    /** 拣货员（员工 id） */
    private Long staffId;
    /** 拣货员「工号 姓名」，如「OP001 张伟」 */
    private String staffName;
    private Integer status;
    private String statusName;
    private Integer capacity;
    private Integer totalQty;
    private Integer totalTasks;
    private Integer pathDistance;

    /** 涉及的订单数（通过 allocation 反查） */
    private Integer orderCount;

    private LocalDateTime createdAt;
    private LocalDateTime finishedAt;

    /** 拣货任务列表（详情接口返回） */
    private List<PickingTaskVO> tasks;
}
