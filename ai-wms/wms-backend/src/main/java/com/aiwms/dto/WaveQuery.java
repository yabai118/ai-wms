package com.aiwms.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class WaveQuery extends PageQuery {

    private String waveNo;

    /** 0待拣货 1拣货中 2已完成 */
    private Integer status;
}
