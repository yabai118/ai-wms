package com.aiwms.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 首页看板数据
 */
@Data
public class DashboardVO {

    /** 核心指标 */
    private Map<String, Object> summary;

    /** 订单趋势（按天） */
    private List<Map<String, Object>> orderTrend;

    /** ABC 分类分布 */
    private List<Map<String, Object>> abcDistribution;

    /** 库区占用情况 */
    private List<Map<String, Object>> areaUsage;

    /** 库位类型分布 */
    private List<Map<String, Object>> locationType;

    /** 库存 TOP10 SKU */
    private List<Map<String, Object>> topStock;

    /** 波次状态分布 */
    private List<Map<String, Object>> waveStatus;
}
