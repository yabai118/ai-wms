package com.aiwms.service.impl;

import com.aiwms.dto.DashboardVO;
import com.aiwms.mapper.DashboardMapper;
import com.aiwms.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private final DashboardMapper dashboardMapper;

    @Override
    public DashboardVO getDashboard() {
        DashboardVO vo = new DashboardVO();
        vo.setSummary(dashboardMapper.summary());
        vo.setOrderTrend(dashboardMapper.orderTrend());
        vo.setAbcDistribution(dashboardMapper.abcDistribution());
        vo.setAreaUsage(dashboardMapper.areaUsage());
        vo.setLocationType(dashboardMapper.locationType());
        vo.setTopStock(dashboardMapper.topStock());
        vo.setWaveStatus(dashboardMapper.waveStatus());
        return vo;
    }
}
